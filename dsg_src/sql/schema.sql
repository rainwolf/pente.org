/*M!999999\- enable the sandbox mode */ 

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;
DROP TABLE IF EXISTS `dsg_donation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_donation` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `amount` decimal(5,2) NOT NULL DEFAULT 0.00,
  `date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `payment_method` char(1) NOT NULL DEFAULT ''
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_email_verification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_email_verification` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `pid` bigint(20) unsigned NOT NULL,
  `verification_code` varchar(512) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `code_key` (`verification_code`),
  KEY `pid_key` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_followers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_followers` (
  `pid` bigint(20) unsigned NOT NULL,
  `follower_pid` bigint(20) NOT NULL,
  PRIMARY KEY (`pid`,`follower_pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_goodies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_goodies` (
  `pid` bigint(20) unsigned NOT NULL,
  `award` int(11) NOT NULL DEFAULT 0,
  `expiration_date` datetime DEFAULT NULL,
  PRIMARY KEY (`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_ip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_ip` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `ip` varchar(45) NOT NULL DEFAULT '',
  `access_time` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `ban` enum('Y','N') NOT NULL DEFAULT 'N',
  KEY `pid` (`pid`,`ban`),
  KEY `ip` (`ip`,`ban`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_koth`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_koth` (
  `pid` bigint(20) unsigned NOT NULL,
  `date` date NOT NULL,
  `streak` int(11) DEFAULT 1,
  KEY `date` (`date`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_live_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_live_set` (
  `sid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `p1_pid` bigint(20) unsigned NOT NULL,
  `p2_pid` bigint(20) unsigned NOT NULL,
  `g1_gid` bigint(20) unsigned DEFAULT NULL,
  `g2_gid` bigint(20) unsigned DEFAULT NULL,
  `status` char(1) NOT NULL,
  `winner` tinyint(3) unsigned DEFAULT NULL,
  `creation_date` datetime NOT NULL,
  `completion_date` datetime DEFAULT NULL,
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_message` (
  `mid` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `from_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `to_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `subject` varchar(255) NOT NULL DEFAULT '',
  `body` longtext NOT NULL,
  `creation_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `read_fl` char(1) NOT NULL DEFAULT 'N',
  `viewable` char(1) NOT NULL DEFAULT 'Y',
  PRIMARY KEY (`mid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_player`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_player` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `password` varchar(32) NOT NULL DEFAULT '',
  `email` varchar(100) NOT NULL DEFAULT '',
  `email_valid` char(1) NOT NULL DEFAULT '',
  `num_logins` int(10) unsigned NOT NULL DEFAULT 0,
  `last_login_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `register_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `de_register_date` datetime DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '',
  `email_visible` char(1) NOT NULL DEFAULT '',
  `location` varchar(100) DEFAULT NULL,
  `sex` char(1) NOT NULL DEFAULT '',
  `age` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `homepage` varchar(100) DEFAULT NULL,
  `name_color` int(11) NOT NULL DEFAULT 0,
  `hash_code` varchar(16) NOT NULL DEFAULT '',
  `last_update_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `player_type` char(1) NOT NULL DEFAULT 'H',
  `note` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `admin` enum('Y','N') NOT NULL DEFAULT 'N',
  `timezone` varchar(100) DEFAULT 'America/New_York',
  `mobile_adult` enum('Y','N') NOT NULL DEFAULT 'Y',
  PRIMARY KEY (`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_player_avatar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_player_avatar` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `avatar` blob NOT NULL,
  `content_type` varchar(100) NOT NULL DEFAULT '',
  `last_update_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_player_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_player_game` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `game` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `wins` int(10) unsigned NOT NULL DEFAULT 0,
  `losses` int(10) unsigned NOT NULL DEFAULT 0,
  `draws` int(10) unsigned NOT NULL DEFAULT 0,
  `rating` decimal(14,9) NOT NULL DEFAULT 0.000000000,
  `streak` smallint(6) NOT NULL DEFAULT 0,
  `last_game_date` datetime DEFAULT NULL,
  `computer` char(1) NOT NULL DEFAULT 'N',
  `tourney_winner` enum('0','1','2','3','4') DEFAULT '0',
  PRIMARY KEY (`pid`,`game`,`computer`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_player_ignore`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_player_ignore` (
  `ignore_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `ignore_pid` bigint(20) NOT NULL DEFAULT 0,
  `ignore_invite` char(1) NOT NULL DEFAULT '',
  `ignore_chat` char(1) NOT NULL DEFAULT '',
  `last_update_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`ignore_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_player_prefs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_player_prefs` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `pref_name` varchar(256) NOT NULL DEFAULT '',
  `pref_value` blob NOT NULL,
  `last_update_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`pid`,`pref_name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_return_email`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_return_email` (
  `message_id` varchar(100) NOT NULL DEFAULT '',
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `email` varchar(100) NOT NULL DEFAULT '',
  `send_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`message_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_server`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_server` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `port` int(11) NOT NULL DEFAULT 0,
  `tournament` enum('Y','N') NOT NULL DEFAULT 'N',
  `active` enum('Y','N') NOT NULL DEFAULT 'Y',
  `creation_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `last_mod_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `private` enum('Y','N') NOT NULL DEFAULT 'N',
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_server_access`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_server_access` (
  `server_id` int(10) unsigned NOT NULL DEFAULT 0,
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`server_id`,`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_server_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_server_game` (
  `server_id` int(10) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `game` tinyint(3) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`server_id`,`event_id`,`game`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_server_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_server_message` (
  `server_id` int(10) unsigned NOT NULL DEFAULT 0,
  `message_seq` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `message` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`server_id`,`message_seq`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_subscribers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_subscribers` (
  `pid` bigint(20) unsigned NOT NULL,
  `level` bigint(20) unsigned NOT NULL DEFAULT 0,
  `paymentdate` datetime NOT NULL,
  `transactionid` char(19) NOT NULL,
  `amount` double NOT NULL DEFAULT 0,
  `verified` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`transactionid`),
  KEY `pid` (`pid`),
  KEY `paymentdate` (`paymentdate`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_subscribers_ios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_subscribers_ios` (
  `pid` bigint(20) unsigned NOT NULL,
  `paymentdate` datetime NOT NULL,
  `receipt` text NOT NULL,
  PRIMARY KEY (`pid`),
  KEY `paymentdate` (`paymentdate`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `signup_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `rating` int(10) unsigned NOT NULL DEFAULT 0,
  `seed` tinyint(4) DEFAULT NULL,
  `dropout_round` tinyint(3) unsigned DEFAULT 0,
  PRIMARY KEY (`pid`,`event_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_admin` (
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_detail` (
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `status` enum('N','S','A','C') NOT NULL DEFAULT 'N',
  `timer` enum('N','S','I') NOT NULL DEFAULT 'N',
  `initial_time` smallint(5) unsigned DEFAULT NULL,
  `incremental_time` smallint(5) unsigned DEFAULT NULL,
  `round_length_days` smallint(5) unsigned DEFAULT NULL,
  `creation_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `signup_end_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `start_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `completion_date` datetime DEFAULT NULL,
  `format` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `speed` enum('Y','N') NOT NULL DEFAULT 'N',
  `forumID` bigint(20) unsigned DEFAULT NULL,
  `prize` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_match`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_match` (
  `mid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `event_id` int(11) NOT NULL DEFAULT 0,
  `round` tinyint(4) NOT NULL DEFAULT 0,
  `section` tinyint(4) NOT NULL DEFAULT 0,
  `gid` bigint(20) unsigned DEFAULT NULL,
  `p1_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `p2_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `result` enum('1','2','3','4') DEFAULT NULL,
  `match_seq` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `forfeit` enum('Y','N') NOT NULL DEFAULT 'N',
  PRIMARY KEY (`mid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_player`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_player` (
  `event_id` int(11) NOT NULL DEFAULT 0,
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `signup_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `seed` int(10) unsigned DEFAULT NULL,
  PRIMARY KEY (`event_id`,`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_restriction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_restriction` (
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `type` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `value` smallint(5) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`event_id`,`type`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dsg_tournament_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dsg_tournament_results` (
  `result_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pid1` bigint(20) unsigned NOT NULL DEFAULT 0,
  `pid2` bigint(20) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `round` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `section` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `result` enum('0','1','2','3') NOT NULL DEFAULT '0',
  `forfeit` enum('Y','N') NOT NULL DEFAULT 'N',
  `p1_wins` tinyint(4) NOT NULL DEFAULT 0,
  `p1_losses` tinyint(4) NOT NULL DEFAULT 0,
  `p2_wins` tinyint(4) NOT NULL DEFAULT 0,
  `p2_losses` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`result_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `game_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_event` (
  `eid` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `site_id` smallint(5) unsigned NOT NULL DEFAULT 0,
  `mailing_list` varchar(30) DEFAULT NULL,
  `game` tinyint(3) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`eid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `game_site`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_site` (
  `sid` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `short_name` varchar(10) NOT NULL DEFAULT '',
  `URL` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveAttachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveAttachment` (
  `attachmentID` bigint(20) NOT NULL DEFAULT 0,
  `messageID` bigint(20) NOT NULL DEFAULT 0,
  `fileName` varchar(255) NOT NULL DEFAULT '',
  `fileSize` int(11) NOT NULL DEFAULT 0,
  `contentType` varchar(50) NOT NULL DEFAULT '',
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`attachmentID`),
  KEY `jiveAttachment_messageID_idx` (`messageID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveAttachmentProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveAttachmentProp` (
  `attachmentID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`attachmentID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveCategory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveCategory` (
  `categoryID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(255) NOT NULL DEFAULT '',
  `description` text DEFAULT NULL,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  `lft` int(11) NOT NULL DEFAULT 0,
  `rgt` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`categoryID`),
  KEY `jiveCategory_lft_idx` (`lft`),
  KEY `jiveCategory_rgt_idx` (`rgt`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveCategoryProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveCategoryProp` (
  `categoryID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`categoryID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveForum`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveForum` (
  `forumID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(255) NOT NULL DEFAULT '',
  `description` text DEFAULT NULL,
  `modDefaultThreadVal` bigint(20) NOT NULL DEFAULT 0,
  `modMinThreadVal` bigint(20) NOT NULL DEFAULT 0,
  `modDefaultMsgVal` bigint(20) NOT NULL DEFAULT 0,
  `modMinMsgVal` bigint(20) NOT NULL DEFAULT 0,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  `categoryID` bigint(20) NOT NULL DEFAULT 1,
  `categoryIndex` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`forumID`),
  KEY `jiveForum_name_idx` (`name`(10)),
  KEY `jiveForum_cat_idx` (`categoryID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveForumProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveForumProp` (
  `forumID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`forumID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveGroup` (
  `groupID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(50) NOT NULL DEFAULT '',
  `description` varchar(255) DEFAULT NULL,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`groupID`),
  KEY `jiveGroup_name_idx` (`name`(10)),
  KEY `jiveGroup_cDate_idx` (`creationDate`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveGroupPerm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveGroupPerm` (
  `objectType` int(11) NOT NULL DEFAULT 0,
  `objectID` bigint(20) NOT NULL DEFAULT 0,
  `groupID` bigint(20) NOT NULL DEFAULT 0,
  `permission` int(11) NOT NULL DEFAULT 0,
  KEY `jiveGroupPerm_object_idx` (`objectType`,`objectID`),
  KEY `jiveGroupPerm_groupID_idx` (`groupID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveGroupProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveGroupProp` (
  `groupID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`groupID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveGroupUser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveGroupUser` (
  `groupID` bigint(20) NOT NULL DEFAULT 0,
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `administrator` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`groupID`,`userID`,`administrator`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveID`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveID` (
  `idType` int(11) NOT NULL DEFAULT 0,
  `id` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`idType`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveMessage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveMessage` (
  `messageID` bigint(20) NOT NULL DEFAULT 0,
  `parentMessageID` bigint(20) DEFAULT NULL,
  `threadID` bigint(20) NOT NULL DEFAULT 0,
  `forumID` bigint(20) NOT NULL DEFAULT 0,
  `userID` bigint(20) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `body` text DEFAULT NULL,
  `modValue` bigint(20) NOT NULL DEFAULT 0,
  `rewardPoints` int(11) NOT NULL DEFAULT 0,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`messageID`),
  KEY `jiveMessage_threadID_idx` (`threadID`),
  KEY `jiveMessage_forumID_idx` (`forumID`),
  KEY `jiveMessage_userID_idx` (`userID`),
  KEY `jiveMessage_modValue_idx` (`modValue`),
  KEY `jiveMessage_cDate_idx` (`creationDate`),
  KEY `jiveMessage_mDate_idx` (`modificationDate`),
  KEY `jiveMessage_forumID_modVal_idx` (`forumID`,`modValue`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveMessageProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveMessageProp` (
  `messageID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`messageID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveModeration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveModeration` (
  `objectID` bigint(20) NOT NULL DEFAULT 0,
  `objectType` bigint(20) NOT NULL DEFAULT 0,
  `userID` bigint(20) DEFAULT NULL,
  `modDate` varchar(15) NOT NULL DEFAULT '',
  `modValue` bigint(20) NOT NULL DEFAULT 0,
  KEY `jiveModeration_objectID_idx` (`objectID`),
  KEY `jiveModeration_objectType_idx` (`objectType`),
  KEY `jiveModeration_userID_idx` (`userID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveReadTracker`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveReadTracker` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `objectType` int(11) NOT NULL DEFAULT 0,
  `objectID` bigint(20) NOT NULL DEFAULT 0,
  `readDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`userID`,`objectType`,`objectID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveReward`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveReward` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `rewardPoints` bigint(20) NOT NULL DEFAULT 0,
  `messageID` bigint(20) DEFAULT NULL,
  `threadID` bigint(20) DEFAULT NULL,
  KEY `jiveReward_userID_idx` (`userID`),
  KEY `jiveReward_creationDate_idx` (`creationDate`),
  KEY `jiveReward_messageID_idx` (`messageID`),
  KEY `jiveReward_threadID_idx` (`threadID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveThread`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveThread` (
  `threadID` bigint(20) NOT NULL DEFAULT 0,
  `forumID` bigint(20) NOT NULL DEFAULT 0,
  `rootMessageID` bigint(20) NOT NULL DEFAULT 0,
  `modValue` bigint(20) NOT NULL DEFAULT 0,
  `rewardPoints` int(11) NOT NULL DEFAULT 0,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`threadID`),
  KEY `jiveThread_forumID_idx` (`forumID`),
  KEY `jiveThread_modValue_idx` (`modValue`),
  KEY `jiveThread_cDate_idx` (`creationDate`),
  KEY `jiveThread_mDate_idx` (`modificationDate`),
  KEY `jiveThread_fID_mV_idx` (`forumID`,`modValue`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveThreadProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveThreadProp` (
  `threadID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`threadID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveUser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveUser` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `username` varchar(30) NOT NULL DEFAULT '',
  `passwordHash` varchar(32) NOT NULL DEFAULT '',
  `name` varchar(100) DEFAULT NULL,
  `nameVisible` int(11) NOT NULL DEFAULT 0,
  `email` varchar(100) NOT NULL DEFAULT '',
  `emailVisible` int(11) NOT NULL DEFAULT 0,
  `creationDate` varchar(15) NOT NULL DEFAULT '',
  `modificationDate` varchar(15) NOT NULL DEFAULT '',
  PRIMARY KEY (`userID`),
  UNIQUE KEY `username` (`username`),
  KEY `jiveUser_username_idx` (`username`(10)),
  KEY `jiveUser_cDate_idx` (`creationDate`),
  KEY `jiveUser_hash_idx` (`passwordHash`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveUserPerm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveUserPerm` (
  `objectType` int(11) NOT NULL DEFAULT 0,
  `objectID` bigint(20) NOT NULL DEFAULT 0,
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `permission` int(11) NOT NULL DEFAULT 0,
  KEY `jiveUserPerm_object_idx` (`objectType`,`objectID`),
  KEY `jiveUserPerm_userID_idx` (`userID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveUserProp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveUserProp` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL DEFAULT '',
  `propValue` text NOT NULL,
  PRIMARY KEY (`userID`,`name`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveUserReward`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveUserReward` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `rewardPoints` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`userID`,`rewardPoints`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveUserRoster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveUserRoster` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `subUserID` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`userID`,`subUserID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jiveWatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jiveWatch` (
  `userID` bigint(20) NOT NULL DEFAULT 0,
  `objectID` bigint(20) NOT NULL DEFAULT 0,
  `objectType` bigint(20) NOT NULL DEFAULT 0,
  `watchType` int(11) NOT NULL DEFAULT 0,
  `expirable` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`userID`,`objectID`,`objectType`,`watchType`),
  KEY `jiveWatch_userID_idx` (`userID`),
  KEY `jiveWatch_objectID_idx` (`objectID`),
  KEY `jiveWatch_objectType_idx` (`objectType`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `koth`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `koth` (
  `koth_id` int(8) unsigned NOT NULL,
  `pid` bigint(20) unsigned NOT NULL,
  `step` int(8) unsigned NOT NULL DEFAULT 0,
  `last_game` datetime DEFAULT NULL,
  PRIMARY KEY (`koth_id`,`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `pid` bigint(20) NOT NULL DEFAULT 0,
  `token` varchar(256) NOT NULL DEFAULT '',
  `lastping` datetime DEFAULT NULL,
  `lastnotification` datetime DEFAULT NULL,
  PRIMARY KEY (`pid`,`token`),
  KEY `Index PID` (`pid`),
  KEY `Index token` (`token`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications_android`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications_android` (
  `pid` bigint(20) NOT NULL DEFAULT 0,
  `token` varchar(512) NOT NULL DEFAULT '',
  `lastping` datetime DEFAULT NULL,
  `lastnotification` datetime DEFAULT NULL,
  PRIMARY KEY (`pid`,`token`),
  KEY `Index PID` (`pid`),
  KEY `Index token` (`token`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pente_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pente_game` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `site_id` smallint(5) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `round` varchar(100) DEFAULT NULL,
  `section` varchar(100) DEFAULT NULL,
  `play_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `timer` enum('N','S','I') NOT NULL DEFAULT 'N',
  `rated` enum('Y','N') NOT NULL DEFAULT 'Y',
  `initial_time` smallint(5) unsigned DEFAULT NULL,
  `incremental_time` smallint(5) unsigned DEFAULT NULL,
  `player1_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `player2_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `player1_rating` smallint(5) unsigned DEFAULT NULL,
  `player2_rating` smallint(5) unsigned DEFAULT NULL,
  `winner` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `player1_type` char(1) NOT NULL DEFAULT '0',
  `player2_type` char(1) NOT NULL DEFAULT '0',
  `game` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `swapped` enum('Y','N') NOT NULL DEFAULT 'N',
  `private` enum('Y','N') NOT NULL DEFAULT 'N',
  `set_id` bigint(20) unsigned DEFAULT NULL,
  `status` char(1) DEFAULT NULL,
  `swap2pass` tinyint(1) DEFAULT 0,
  `renju_swaps` smallint(5) unsigned DEFAULT NULL,
  PRIMARY KEY (`gid`),
  KEY `game` (`game`,`site_id`,`event_id`,`round`,`section`),
  KEY `game_2` (`game`,`play_date`,`gid`),
  KEY `game_3` (`game`,`player1_pid`,`gid`),
  KEY `game_4` (`game`,`player2_pid`,`gid`),
  KEY `game_id` (`game`,`gid`,`site_id`),
  KEY `game_rating1` (`player1_rating`,`game`,`gid`),
  KEY `game_rating2` (`player2_rating`,`game`,`gid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
CREATE TABLE `pente_renju_offer` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `site_id` smallint(5) unsigned NOT NULL DEFAULT 0,
  `offer_num` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `move` smallint(5) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`gid`,`site_id`,`offer_num`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
DROP TABLE IF EXISTS `pente_move`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `pente_move` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `move_num` smallint(5) NOT NULL DEFAULT 0,
  `next_move` smallint(5) unsigned NOT NULL DEFAULT 0,
  `hash_key` bigint(20) NOT NULL DEFAULT 0,
  `rotation` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `game` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `winner` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `play_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `seconds_left` smallint(5) unsigned DEFAULT NULL,
  PRIMARY KEY (`gid`,`hash_key`,`move_num`),
  KEY `hash_key` (`hash_key`,`move_num`,`game`,`next_move`,`rotation`,`winner`),
  KEY `hash_key_2` (`hash_key`,`move_num`,`game`,`play_date`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `player`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `player` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  `site_id` smallint(5) unsigned NOT NULL DEFAULT 0,
  `name_lower` varchar(100) NOT NULL DEFAULT '',
  PRIMARY KEY (`pid`),
  KEY `userid_name` (`name`),
  KEY `name_lower` (`name_lower`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `spam_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `spam_messages` (
  `messageID` bigint(20) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `spam_threads`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `spam_threads` (
  `threadID` bigint(20) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `spam_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `spam_users` (
  `pid` bigint(20) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `speed_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `speed_mapping` (
  `normal_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `speed_pid` bigint(20) unsigned NOT NULL DEFAULT 0
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `speed_mapping1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `speed_mapping1` (
  `normal_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `speed_pid` bigint(20) unsigned NOT NULL DEFAULT 0
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_emergency_time`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_emergency_time` (
  `pid` bigint(20) unsigned NOT NULL,
  `hoursLeft` int(10) unsigned NOT NULL,
  `lastPinch` datetime NOT NULL,
  PRIMARY KEY (`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_game` (
  `gid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `state` char(1) NOT NULL DEFAULT 'N',
  `p1_pid` bigint(20) unsigned DEFAULT NULL,
  `p2_pid` bigint(20) unsigned DEFAULT NULL,
  `creation_date` datetime DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
  `last_move_date` datetime DEFAULT NULL,
  `timeout_date` datetime DEFAULT NULL,
  `completion_date` datetime DEFAULT NULL,
  `game` smallint(5) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `round` tinyint(3) unsigned DEFAULT NULL,
  `section` tinyint(3) unsigned DEFAULT NULL,
  `days_per_move` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rated` enum('Y','N') NOT NULL DEFAULT 'Y',
  `inviter_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `winner` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `dpente_state` tinyint(3) unsigned DEFAULT NULL,
  `dpente_swap` enum('Y','N') DEFAULT NULL,
  `hiddenBy` tinyint(1) NOT NULL DEFAULT 0,
  `swap2pass` tinyint(1) DEFAULT 0,
  `renju_swaps` smallint(5) unsigned DEFAULT NULL,
  `renju_offers` varbinary(10) DEFAULT NULL,
  PRIMARY KEY (`gid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_game_ai`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_game_ai` (
  `gid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `state` char(1) NOT NULL DEFAULT 'N',
  `p1_pid` bigint(20) unsigned DEFAULT NULL,
  `p2_pid` bigint(20) unsigned DEFAULT NULL,
  `creation_date` datetime DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
  `last_move_date` datetime DEFAULT NULL,
  `timeout_date` datetime DEFAULT NULL,
  `completion_date` datetime DEFAULT NULL,
  `game` smallint(5) unsigned NOT NULL DEFAULT 0,
  `event_id` int(10) unsigned NOT NULL DEFAULT 0,
  `round` tinyint(3) unsigned DEFAULT NULL,
  `section` tinyint(3) unsigned DEFAULT NULL,
  `days_per_move` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `rated` enum('Y','N') NOT NULL DEFAULT 'Y',
  `inviter_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `winner` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `dpente_state` tinyint(3) unsigned DEFAULT NULL,
  `dpente_swap` enum('Y','N') DEFAULT NULL,
  `hiddenBy` tinyint(1) NOT NULL DEFAULT 0,
  `swap2pass` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`gid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_message` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `seq_nbr` smallint(5) unsigned NOT NULL DEFAULT 0,
  `move_num` smallint(5) unsigned DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_move`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_move` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `move_num` smallint(5) unsigned NOT NULL DEFAULT 0,
  `move` smallint(5) NOT NULL DEFAULT 0,
  PRIMARY KEY (`gid`,`move_num`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_move_ai`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_move_ai` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `move_num` smallint(5) unsigned NOT NULL DEFAULT 0,
  `move` smallint(5) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`gid`,`move_num`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_set` (
  `sid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gid1` bigint(20) unsigned NOT NULL DEFAULT 0,
  `gid2` bigint(20) unsigned DEFAULT NULL,
  `p1_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `p2_pid` bigint(20) unsigned DEFAULT NULL,
  `state` char(1) NOT NULL DEFAULT 'N',
  `creation_date` datetime DEFAULT NULL,
  `completion_date` datetime DEFAULT NULL,
  `inviter_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `cancel_pid` bigint(20) DEFAULT NULL,
  `cancel_msg` varchar(255) DEFAULT NULL,
  `private` enum('Y','N') NOT NULL DEFAULT 'N',
  `invitation_restriction` enum('A','N','L','H','S','C','B') DEFAULT 'A',
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_set_ai`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_set_ai` (
  `sid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gid1` bigint(20) unsigned NOT NULL DEFAULT 0,
  `gid2` bigint(20) unsigned DEFAULT NULL,
  `p1_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `p2_pid` bigint(20) unsigned DEFAULT NULL,
  `state` char(1) NOT NULL DEFAULT 'N',
  `creation_date` datetime DEFAULT NULL,
  `completion_date` datetime DEFAULT NULL,
  `inviter_pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `cancel_pid` bigint(20) DEFAULT NULL,
  `cancel_msg` varchar(255) DEFAULT NULL,
  `private` enum('Y','N') NOT NULL DEFAULT 'N',
  `invitation_restriction` enum('A','N','L','H','S','C') DEFAULT 'A',
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_vacation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_vacation` (
  `pid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `date` date NOT NULL,
  PRIMARY KEY (`pid`,`date`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tb_vacation_floating`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_vacation_floating` (
  `pid` bigint(20) unsigned NOT NULL,
  `daysLeft` int(10) unsigned NOT NULL,
  `lastUpdateYear` int(11) NOT NULL,
  PRIMARY KEY (`pid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `temp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `temp` (
  `pid` bigint(20) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `temp_tb`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `temp_tb` (
  `pid` bigint(20) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
