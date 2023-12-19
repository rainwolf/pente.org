package org.pente.jive;

import com.jivesoftware.base.*;
import com.jivesoftware.base.database.ConnectionManager;
import com.jivesoftware.base.event.UserEvent;
import com.jivesoftware.util.*;

import java.util.*;
import java.util.Date;
import java.sql.*;

import org.apache.log4j.*;

/**
 * An abstract adapter class to aid in creating custom user implementations. The
 * "set" methods in this class throw UnsupportedOperationExceptions, which means
 * the external user store will be read-only. You should extend this class to
 * create your own User implementation and only override the methods of interest.<p>
 * <p>
 * If your user store doesn't support all the fields present in the User interface
 * (such as the name visible and email visible flags), you should use hardcoded
 * values in your implementation or load and store these values from somewhere else.<p>
 * <p>
 * User objects have "extended properties", which is a way to allow arbitrary data
 * to be attached to users. It's generally advisable to use the jiveUserProp table
 * that is built into the Jive database schema to store this information. This adapter
 * class implements all the logic necessary to load and store properties from the
 * jiveUserProp database table.
 *
 * @author Jive Software, 2003
 */

@SuppressWarnings("unchecked")
public abstract class SimpleUserAdapter implements User, Cacheable {

    private static final Category log4j =
            Category.getInstance(SimpleUserAdapter.class.getName());

    // Database queries for property loading and setting.

    private static final String LOAD_PROPERTIES =
            "SELECT name, propValue FROM jiveUserProp WHERE userID=?";
    private static final String DELETE_PROPERTY =
            "DELETE FROM jiveUserProp WHERE userID=? AND name=?";
    private static final String UPDATE_PROPERTY =
            "UPDATE jiveUserProp SET propValue=? WHERE name=? AND userID=?";
    private static final String INSERT_PROPERTY =
            "INSERT INTO jiveUserProp (userID, name, propValue) VALUES (?, ?, ?)";

    // Constant values for permissions.
    private static final Permissions USER_ADMIN_PERMS = new Permissions(Permissions.USER_ADMIN);
    private static final Permissions NO_PERMS = new Permissions(Permissions.NONE);

    protected long ID = -1;
    protected String username;
    protected String name;
    protected String email;

    // Use hardcoded values for these properties.
    protected boolean nameVisible = true;
    protected boolean emailVisible = false;
    protected Date creationDate, modificationDate = new java.util.Date();

    protected Map<String, String> properties = null;
    private Object propertiesLock = new Object();

    // User Interface -- note, Javadoc descriptions are left off so that
    // they will be copied from the User interface.

    public long getID() {
        return ID;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public void setPassword(String password) {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public String getPasswordHash() {
        // Not implemented.
        throw new UnsupportedOperationException();
    }

    public void setPasswordHash(String passwordHash) {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public String getEmail() {
        return StringUtils.escapeHTMLTags(email);
    }

    public void setEmail(String email) {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public boolean isNameVisible() {
        return nameVisible;
    }

    public void setNameVisible(boolean visible) throws UnauthorizedException {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public boolean isEmailVisible() {
        return emailVisible;
    }

    public void setEmailVisible(boolean visible) throws UnauthorizedException {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) throws UnauthorizedException {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) throws UnauthorizedException {
        // Setting user data not supported.
        throw new UnsupportedOperationException();
    }

    public String getProperty(String name) {
        // Lazy-load properties.
        synchronized (propertiesLock) {
            if (properties == null) {
                loadPropertiesFromDb();
            }
        }
        return (String) properties.get(name);
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        // Lazy-load properties.
        synchronized (propertiesLock) {
            if (properties == null) {
                loadPropertiesFromDb();
            }
        }
        // Make sure the property name and value aren't null.
        if (name == null || value == null || "".equals(name) || "".equals(value)) {
            throw new NullPointerException("Cannot set property with empty or null value.");
        }
        // See if we need to update a property value or insert a new one.
        if (properties.containsKey(name)) {
            // Only update the value in the database if the property value
            // has changed.
            if (!(value.equals(properties.get(name)))) {
                String original = (String) properties.get(name);
                properties.put(name, value);
                updatePropertyInDb(name, value);

                // Re-add user to cache.
                UserManagerFactory.userCache.put(Long.valueOf(ID), this);

                // fire off an event
                Map<String, String> params = new HashMap<>();
                params.put("Type", "propertyModify");
                params.put("PropertyKey", name);
                params.put("originalValue", original);
                UserEvent event = new UserEvent(UserEvent.USER_MODIFIED, this, params);
                EventDispatcher.getInstance().notifyListeners(event);
            }
        } else {
            properties.put(name, value);
            insertPropertyIntoDb(name, value);

            // Re-add user to cache.
            UserManagerFactory.userCache.put(Long.valueOf(ID), this);

            // fire off an event
            Map<String, String> params = new HashMap<>();
            params.put("Type", "propertyAdd");
            params.put("PropertyKey", name);
            UserEvent event = new UserEvent(UserEvent.USER_MODIFIED, this, params);
            EventDispatcher.getInstance().notifyListeners(event);
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        // Lazy-load properties.
        synchronized (propertiesLock) {
            if (properties == null) {
                loadPropertiesFromDb();
            }
        }
        if (properties.containsKey(name)) {
            // fire off an event
            Map<String, String> params = new HashMap<>();
            params.put("Type", "propertyDelete");
            params.put("PropertyKey", name);
            UserEvent event = new UserEvent(UserEvent.USER_MODIFIED, this, params);
            EventDispatcher.getInstance().notifyListeners(event);

            properties.remove(name);
            deletePropertyFromDb(name);

            // Re-add user to cache.
            UserManagerFactory.userCache.put(Long.valueOf(ID), this);
        }
    }

    public Iterator<String> getPropertyNames() {
        // Lazy-load properties.
        synchronized (propertiesLock) {
            if (properties == null) {
                loadPropertiesFromDb();
            }
        }
        return Collections.unmodifiableSet(properties.keySet()).iterator();
    }

    public boolean isAuthorized(long permissionType) {
        // Always return true. A protection proxy will wrap the User
        // object to provide real permissions checking.
        return true;
    }

    public Permissions getPermissions(AuthToken authToken) {

        // A user is allowed to administer themselves.
        if (authToken.getUserID() == ID) {
            log4j.debug("getPermissions return USER_ADMIN_PERMS");
            return USER_ADMIN_PERMS;
        } else {
            log4j.debug("getPermissions return NO_PERMS");
            return NO_PERMS;
        }
    }

    // Cacheable Interface

    public int getCachedSize() {
        // Every item put into cache must be able to calculate it's own size. The "size" of 
        // the object is calculated by adding up the sizes of all its member variables.
        // The CacheSizes class can be used to help in this calculation.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // ID
        size += CacheSizes.sizeOfString(username);      // username
        size += CacheSizes.sizeOfString(name);          // name
        size += CacheSizes.sizeOfString(email);         // email
        size += CacheSizes.sizeOfBoolean();             // nameVisible
        size += CacheSizes.sizeOfBoolean();             // emailVisible
        size += CacheSizes.sizeOfMap(properties);       // properties
        size += CacheSizes.sizeOfObject();              // properties lock
        size += CacheSizes.sizeOfDate();                // creationDate
        size += CacheSizes.sizeOfDate();                // modificationDate

        return size;
    }

    // Other Methods

    public String toString() {
        return username;
    }

    public int hashCode() {
        return (int) ID;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof User) {
            return ID == ((User) object).getID();
        } else {
            return false;
        }
    }

    /**
     * Loads properties from the database.
     */
    private void loadPropertiesFromDb() {
        this.properties = new Hashtable<>();
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setLong(1, ID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                properties.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Deletes a property from the db.
     */
    private void deletePropertyFromDb(String name) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setLong(1, ID);
            pstmt.setString(2, name);
            pstmt.execute();
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Inserts a new property into the datatabase.
     */
    private void insertPropertyIntoDb(String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setLong(1, ID);
            pstmt.setString(2, name);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Updates a property value in the database.
     */
    private void updatePropertyInDb(String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = ConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, value);
            pstmt.setString(2, name);
            pstmt.setLong(3, ID);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Log.error(e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }
}