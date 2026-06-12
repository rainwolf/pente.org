/* see tracker bug #444785 */
/* this script checks for clashed names in the same site */
/* it will only work if you've first run fix_clashed_names.sql */

select p1.name, p2.name
from player p1, player p2
where p1.name != p2.name
and lower(p1.name) = lower(p2.name)
and p1.site_id = p2.site_id
order by p1.name;
