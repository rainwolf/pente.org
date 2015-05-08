# $RCSfile: jive_forums_mysql.sql,v $
# $Revision: 1.4.2.1 $
# $Date: 2003/01/16 13:46:18 $

CREATE TABLE jiveCategory (
  categoryID        BIGINT NOT NULL,
  name              VARCHAR(255) NOT NULL,
  description       TEXT,
  creationDate      VARCHAR(15) NOT NULL,
  modificationDate  VARCHAR(15) NOT NULL,
  lft               INT NOT NULL,
  rgt               INT NOT NULL,
  PRIMARY KEY       (categoryID),
  INDEX jiveCategory_lft_idx (lft),
  INDEX jiveCategory_rgt_idx (rgt)
);

CREATE TABLE jiveCategoryProp (
  categoryID        BIGINT NOT NULL,
  name              VARCHAR(100) NOT NULL,
  propValue         TEXT NOT NULL,
  PRIMARY KEY       (categoryID,name)
);

CREATE TABLE jiveForum (
  forumID               BIGINT NOT NULL,
  name                  VARCHAR(255) NOT NULL,
  description           TEXT,
  modDefaultThreadVal   BIGINT NOT NULL,
  modMinThreadVal       BIGINT NOT NULL,
  modDefaultMsgVal      BIGINT NOT NULL,
  modMinMsgVal          BIGINT NOT NULL,
  creationDate          VARCHAR(15) NOT NULL,
  modificationDate      VARCHAR(15) NOT NULL,
  categoryID            BIGINT NOT NULL,
  categoryIndex         INT NOT NULL,
  PRIMARY KEY               (forumID),
  INDEX jiveForum_name_idx  (name(10)),
  INDEX jiveForum_cat_idx   (categoryID)
);

CREATE TABLE jiveForumProp (
  forumID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (forumID,name)
);

CREATE TABLE jiveThread (
  threadID          BIGINT NOT NULL,
  forumID           BIGINT NOT NULL,
  rootMessageID     BIGINT NOT NULL,
  modValue          BIGINT NOT NULL,
  rewardPoints      INT NOT NULL,
  creationDate      VARCHAR(15) NOT NULL,
  modificationDate  VARCHAR(15) NOT NULL,
  PRIMARY KEY       (threadID),
  INDEX jiveThread_forumID_idx (forumID),
  INDEX jiveThread_modValue_idx (modValue),
  INDEX jiveThread_cDate_idx   (creationDate),
  INDEX jiveThread_mDate_idx   (modificationDate),
  INDEX jiveThread_forumID_modVal_idx (forumID, modValue)
);

CREATE TABLE jiveThreadProp (
  threadID      BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (threadID,name)
);

CREATE TABLE jiveMessage (
  messageID             BIGINT NOT NULL,
  parentMessageID       BIGINT NULL,
  threadID              BIGINT NOT NULL,
  forumID               BIGINT NOT NULL,
  userID                BIGINT NULL,
  subject               VARCHAR(255),
  body                  TEXT,
  modValue              BIGINT NOT NULL,
  rewardPoints          INT NOT NULL,
  creationDate          VARCHAR(15) NOT NULL,
  modificationDate      VARCHAR(15) NOT NULL,
  PRIMARY KEY           (messageID),
  INDEX jiveMessage_forumID_idx   (forumID),
  INDEX jiveMessage_threadID_idx  (threadID),
  INDEX jiveMessage_userID_idx    (userID),
  INDEX jiveMessage_forumID_modVal_idx (forumID, modValue),
  INDEX jiveMessage_modValue_idx  (modValue),
  INDEX jiveMessage_cDate_idx     (creationDate),
  INDEX jiveMessage_mDate_idx     (modificationDate)
);

CREATE TABLE jiveMessageProp (
  messageID    BIGINT NOT NULL,
  name         VARCHAR(100) NOT NULL,
  propValue    TEXT NOT NULL,
  PRIMARY KEY  (messageID,name)
);

CREATE TABLE jiveUser (
  userID              BIGINT NOT NULL,
  username            VARCHAR(30) UNIQUE NOT NULL,
  passwordHash        VARCHAR(32) NOT NULL,
  name                VARCHAR(100),
  nameVisible         INT NOT NULL,
  email               VARCHAR(100) NOT NULL,
  emailVisible        INT NOT NULL,
  creationDate        VARCHAR(15) NOT NULL,
  modificationDate    VARCHAR(15) NOT NULL,
  PRIMARY KEY     (userID),
  INDEX jiveUser_username_idx (username(10)),
  INDEX jiveUser_hash_idx (passwordHash),
  INDEX jiveUser_cDate_idx    (creationDate)
);

CREATE TABLE jiveUserPerm (
  objectType   INT NOT NULL,
  objectID     BIGINT NOT NULL,
  userID       BIGINT NOT NULL,
  permission   INT NOT NULL,
  INDEX jiveUserPerm_object_idx (objectType, objectID),
  INDEX jiveUserPerm_userID_idx (userID)
);

CREATE TABLE jiveUserProp (
  userID        BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (userID,name)
);

CREATE TABLE jiveGroup (
  groupID           BIGINT NOT NULL,
  name              VARCHAR(50) NOT NULL,
  description       VARCHAR(255),
  creationDate      VARCHAR(15) NOT NULL,
  modificationDate  VARCHAR(15) NOT NULL,
  PRIMARY KEY   (groupID),
  INDEX jiveGroup_name_idx  (name(10)),
  INDEX jiveGroup_cDate_idx (creationDate)
);

CREATE TABLE jiveGroupPerm (
  objectType   INT NOT NULL,
  objectID     BIGINT NOT NULL,
  groupID      BIGINT NOT NULL,
  permission   INT NOT NULL,
  INDEX jiveGroupPerm_object_idx (objectType, objectID),
  INDEX jiveGroupPerm_groupID_idx  (groupID)
);

CREATE TABLE jiveGroupProp (
  groupID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (groupID,name)
);

CREATE TABLE jiveGroupUser (
  groupID        BIGINT NOT NULL,
  userID         BIGINT NOT NULL,
  administrator  INT NOT NULL,
  PRIMARY KEY   (groupID,userID,administrator)
);

CREATE TABLE jiveID (
  idType        INT NOT NULL,
  id            BIGINT NOT NULL,
  PRIMARY KEY   (idType)
);

CREATE TABLE jiveModeration (
  objectType  BIGINT NOT NULL,
  objectID    BIGINT NOT NULL,
  userID      BIGINT NULL,
  modDate     VARCHAR(15) NOT NULL,
  modValue    BIGINT NOT NULL,
  INDEX jiveModeration_objectID_idx (objectID),
  INDEX jiveModeration_objectType_idx (objectType),
  INDEX jiveModeration_userID_idx (userID)
);

CREATE TABLE jiveWatch (
  userID          BIGINT NOT NULL,
  objectID        BIGINT NOT NULL,
  objectType      BIGINT NOT NULL,
  watchType       INT NOT NULL,
  expirable       INT NOT NULL,
  PRIMARY KEY   (userID, objectID, objectType, watchType),
  INDEX jiveWatch_userID_idx (userID),
  INDEX jiveWatch_objectID_idx (objectID),
  INDEX jiveWatch_objectType_idx (objectType)
);

CREATE TABLE jiveReward (
  userID          BIGINT NOT NULL,
  creationDate    VARCHAR(15) NOT NULL,
  rewardPoints    BIGINT NOT NULL,
  messageID       BIGINT NULL,
  threadID        BIGINT NULL,
  INDEX jiveReward_userID_idx (userID),
  INDEX jiveReward_creationDate_idx (creationDate),
  INDEX jiveReward_messageID_idx (messageID),
  INDEX jiveReward_threadID_idx (threadID)
);

CREATE TABLE jiveUserReward (
  userID        BIGINT NOT NULL,
  rewardPoints  INT NOT NULL,
  PRIMARY KEY   (userID, rewardPoints)
);

CREATE TABLE jiveAttachment (
  attachmentID      BIGINT NOT NULL,
  messageID         BIGINT NOT NULL,
  fileName          VARCHAR(255) NOT NULL,
  fileSize          INT NOT NULL,
  contentType       VARCHAR(50) NOT NULL,
  creationDate      VARCHAR(15) NOT NULL,
  modificationDate  VARCHAR(15) NOT NULL,
  PRIMARY KEY (attachmentID),
  INDEX jiveAttachment_messageID_idx (messageID)
);

CREATE TABLE jiveAttachmentProp (
  attachmentID  BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (attachmentID,name)
);

CREATE TABLE jiveUserRoster (
  userID        BIGINT NOT NULL,
  subUserID     BIGINT NOT NULL,
  PRIMARY KEY   (userID, subUserID)
);

CREATE TABLE jiveReadTracker (
  userID            BIGINT NOT NULL,
  objectType        INT NOT NULL,  
  objectID          BIGINT NOT NULL,
  readDate          VARCHAR(15) NOT NULL,
  PRIMARY KEY       (userID, objectType, objectID)
);

# Finally, insert default table values.

insert into jiveID values (0, 1);
insert into jiveID values (1, 1);
insert into jiveID values (2, 1);
insert into jiveID values (3, 2);
insert into jiveID values (4, 1);
insert into jiveID values (13, 1);
insert into jiveID values (14, 2);

insert into jiveUser (userID,name,username,passwordHash,email,emailVisible,nameVisible,creationDate,modificationDate)
    values (1,'Administrator','admin','21232f297a57a5a743894a0e4a801fc3','admin@example.com',1,1,'0','0');

insert into jiveUserPerm(objectType,objectID,userID,permission) values (17,-1,1,59);
insert into jiveUserPerm(objectType,objectID,userID,permission) values (17,-1,-1,0);
insert into jiveUserPerm(objectType,objectID,userID,permission) values (17,-1,0,1);
insert into jiveUserPerm(objectType,objectID,userID,permission) values (17,-1,0,2);

insert into jiveCategory values (1, 'root', ' ', '0', '0', 1, 2);