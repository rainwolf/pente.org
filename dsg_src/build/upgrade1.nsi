;NSIS Modern User Interface
;Welcome/Finish Page Example Script
;Written by Joost Verburg

 !define SRC_DIR "\dsg_src"

!define JRE_VERSION "1.6.0"

Var InstallJRE
Var JREPath


;--------------------------------
;Include Modern UI

  !include "MUI2.nsh"
  !include "InstallOptions.nsh"

;--------------------------------
;General

  ;Name and file
  Name "Pente Db"
  OutFile "pentedb-update-071108.exe"

  ;Default installation folder
  InstallDir "$PROGRAMFILES\pentedb"

  ;Get installation folder from registry if available
  InstallDirRegKey HKCU "Software\pentedb" ""

  BrandingText " "
  ShowInstDetails show
 
;--------------------------------
;Interface Settings

  !define MUI_ABORTWARNING

;--------------------------------
;Pages
  !define MUI_WELCOMEPAGE_TEXT "The wizard will guide your through the upgrade of Pente Db$\n$\nClick Next to continue."
  
  !define MUI_ICON "z:\dsg_src\images\logo.ico"
  
  !insertmacro MUI_PAGE_WELCOME

  
; This page checks for JRE. It displays a dialog based on JRE.ini if it needs to install JRE
  ; Otherwise you won't see it.
  Page custom CheckInstalledJRE

  !insertmacro MUI_PAGE_DIRECTORY
  !insertmacro MUI_PAGE_INSTFILES
  
    !define MUI_FINISHPAGE_RUN
    !define MUI_FINISHPAGE_RUN_CHECKED
    !define MUI_FINISHPAGE_RUN_TEXT "Start Pente Db"
    !define MUI_FINISHPAGE_RUN_FUNCTION "LaunchLink"
  !insertmacro MUI_PAGE_FINISH

  !insertmacro MUI_UNPAGE_WELCOME
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES
  
  !define MUI_FINISHPAGE_NOAUTOCLOSE
  !insertmacro MUI_UNPAGE_FINISH

;--------------------------------
;Languages

  !insertmacro MUI_LANGUAGE "English"
  
  ;Header
  LangString TEXT_JRE_TITLE ${LANG_ENGLISH} "Java Runtime Environment"
  LangString TEXT_JRE_SUBTITLE ${LANG_ENGLISH} "Installation"
  LangString TEXT_PRODVER_TITLE ${LANG_ENGLISH} "Installed version of Pente Db"
  LangString TEXT_PRODVER_SUBTITLE ${LANG_ENGLISH} "Installation cancelled"
 
 ;--------------------------------
;Reserve Files
 
  ;Only useful for BZIP2 compression
 
  ReserveFile "jre.ini"
!insertmacro MUI_RESERVEFILE_LANGDLL ;Language selection dialog
;old  !insertmacro MUI_RESERVEFILE_INSTALLOPTIONS
 
;--------------------------------
 
;--------------------------------
;Installer Sections

Section "Pente update files" pentedb 
  SectionIn RO
  SetOutPath "$INSTDIR"
  AddSize 1800

  File \dsg_src\build\pentedb.exe
  File \tmp\db2.jar

  File \dsg_src\mmai\Ai.dll
  File \dsg_src\conf\marksAI\pente.scs
  File \dsg_src\conf\marksAI\pente.tbl
  File \dsg_src\db2\mmai.sgf

  ;Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

SectionEnd

;--------------------------------
;Descriptions

  ;Language strings
  LangString DESC_pentedb ${LANG_ENGLISH} "The core Pente Db files (required)."
  LangString DESC_pentePro ${LANG_ENGLISH} "A database of pro level Pente games."
  LangString DESC_shortcut ${LANG_ENGLISH} "Start menu shortcuts to Pente Db."

  ;Assign language strings to sections
  !insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${pentedb} $(DESC_pentedb)
    !insertmacro MUI_DESCRIPTION_TEXT ${pentepro} $(DESC_pentePro)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecCreateShortcut} $(DESC_shortcut)
  !insertmacro MUI_FUNCTION_DESCRIPTION_END

;--------------------------------
;Uninstaller Section

Section "Uninstall"

  RMDir /r /REBOOTOK "$INSTDIR\db"

  Delete /REBOOTOK "$INSTDIR\pentedb.exe"
  Delete /REBOOTOK "$INSTDIR\log4j-1.2.8.jar"
  Delete /REBOOTOK "$INSTDIR\derby.jar"
  Delete /REBOOTOK "$INSTDIR\db2.jar"
  Delete /REBOOTOK "$INSTDIR\swingx-0.9.1.jar"
  Delete /REBOOTOK "$INSTDIR\splash.png"
  Delete /REBOOTOK "$INSTDIR\sample.sgf"
  Delete /REBOOTOK "$INSTDIR\Ai.dll"
  Delete /REBOOTOK "$INSTDIR\pente.scs"
  Delete /REBOOTOK "$INSTDIR\pente.tbl"
  Delete /REBOOTOK "$INSTDIR\mmai.sgf"
  Delete /REBOOTOK "$INSTDIR\Uninstall.exe"
  Delete /REBOOTOK "$INSTDIR\derby.log"
  Delete /REBOOTOK "$INSTDIR\err.log"
  Delete /REBOOTOK "$INSTDIR\out.log"
  Delete /REBOOTOK "$INSTDIR\log4j.properties"

  RMDir "$INSTDIR"

  Delete "$SMPROGRAMS\Pente Db\*"
  RMDir "$SMPROGRAMS\Pente Db"
  
  DeleteRegKey /ifempty HKCU "Software\pentedb"
  DeleteRegKey /ifempty HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\pentedb"
  DeleteRegKey /ifempty HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\pentedb"
  DeleteRegKey /ifempty HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\pentedb"
  DeleteRegKey /ifempty HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\pentedb"
  
SectionEnd


Function .onInit
 
  ;Extract InstallOptions INI Files
  !insertmacro INSTALLOPTIONS_EXTRACT "jre.ini"

FunctionEnd
 

 
Function CheckInstalledJRE
  ;MessageBox MB_OK "Checking Installed JRE Version"
  Push "${JRE_VERSION}"
  Call DetectJRE
  ;Messagebox MB_OK "Done checking JRE version"
  Exch $0	; Get return value from stack
  StrCmp $0 "0" NoFound
  StrCmp $0 "-1" FoundOld
  Goto JREAlreadyInstalled
  
FoundOld:
;  MessageBox MB_OK "Old JRE found"
  !insertmacro INSTALLOPTIONS_WRITE "jre.ini" "Field 1" "Text" "Pente Db requires a more recent version of the Java Runtime Environment\r\nthan the one found on your computer.\r\n\r\nPlease install JRE v${JRE_VERSION} or \r\ndownload the Pente Db installer that comes with a JRE."
  !insertmacro MUI_HEADER_TEXT "$(TEXT_JRE_TITLE)" "$(TEXT_JRE_SUBTITLE)"
  !insertmacro INSTALLOPTIONS_DISPLAY_RETURN "jre.ini"
  Goto MustInstallJRE
 
NoFound:
;  MessageBox MB_OK "JRE not found"
  !insertmacro INSTALLOPTIONS_WRITE "jre.ini" "Field 1" "Text" "No Java Runtime Environment could be found on your computer.\r\n\r\nPlease install JRE v${JRE_VERSION} or \r\ndownload the Pente Db installer that comes with a JRE."
  !insertmacro MUI_HEADER_TEXT "$(TEXT_JRE_TITLE)" "$(TEXT_JRE_SUBTITLE)"
  !insertmacro INSTALLOPTIONS_DISPLAY_RETURN "jre.ini"
  Goto MustInstallJRE
 
MustInstallJRE:
  Exch $0	; $0 now has the installoptions page return value
  ; Do something with return value here
  Pop $0	; Restore $0
  StrCpy $InstallJRE "yes"
  Return
  
JREAlreadyInstalled:
;  MessageBox MB_OK "No download: ${TEMP2}"
  ;MessageBox MB_OK "JRE already installed"
  StrCpy $InstallJRE "no"
  StrCpy $JREPath $0
 ; MessageBox MB_OK "Found: $JREPath"
  !insertmacro INSTALLOPTIONS_WRITE "jre.ini" "UserDefinedSection" "JREPath" $JREPath
  Pop $0		; Restore $0
  Return
 
FunctionEnd
 
; Returns: 0 - JRE not found. -1 - JRE found but too old. Otherwise - Path to JAVA EXE
 
; DetectJRE. Version requested is on the stack.
; Returns (on stack)	"0" on failure (java too old or not installed), otherwise path to java interpreter
; Stack value will be overwritten!
 
Function DetectJRE
  Exch $0	; Get version requested  
		; Now the previous value of $0 is on the stack, and the asked for version of JDK is in $0
  Push $1	; $1 = Java version string (ie 1.5.0)
  Push $2	; $2 = Javahome
  Push $3	; $3 and $4 are used for checking the major/minor version of java
  Push $4
  ;MessageBox MB_OK "Detecting JRE"
  ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  ;MessageBox MB_OK "Read : $1"
  StrCmp $1 "" DetectTry2
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$1" "JavaHome"
  ;MessageBox MB_OK "Read 3: $2"
  StrCmp $2 "" DetectTry2
  Goto GetJRE
 
DetectTry2:
  ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Development Kit" "CurrentVersion"
  ;MessageBox MB_OK "Detect Read : $1"
  StrCmp $1 "" NoFound
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Development Kit\$1" "JavaHome"
  ;MessageBox MB_OK "Detect Read 3: $2"
  StrCmp $2 "" NoFound
 
GetJRE:
; $0 = version requested. $1 = version found. $2 = javaHome
  ;MessageBox MB_OK "Getting JRE"
  IfFileExists "$2\bin\java.exe" 0 NoFound
  StrCpy $3 $0 1			; Get major version. Example: $1 = 1.5.0, now $3 = 1
  StrCpy $4 $1 1			; $3 = major version requested, $4 = major version found
  ;MessageBox MB_OK "Want $3 , found $4"
  IntCmp $4 $3 0 FoundOld FoundNew
  StrCpy $3 $0 1 2
  StrCpy $4 $1 1 2			; Same as above. $3 is minor version requested, $4 is minor version installed
  ;MessageBox MB_OK "Want $3 , found $4" 
  IntCmp $4 $3 FoundNew FoundOld FoundNew
 
NoFound:
;  MessageBox MB_OK "JRE not found"
  Push "0"
  Goto DetectJREEnd
 
FoundOld:
;  MessageBox MB_OK "JRE too old: $3 is older than $4"
;  Push ${TEMP2}
  Push "-1"
  Goto DetectJREEnd  
FoundNew:
  ;MessageBox MB_OK "JRE is new: $3 is newer than $4"
 
  Push "$2\bin\javaw.exe"
;  Push "OK"
;  Return
   Goto DetectJREEnd
DetectJREEnd:
	; Top of stack is return value, then r4,r3,r2,r1
	Exch	; => r4,rv,r3,r2,r1,r0
	Pop $4	; => rv,r3,r2,r1r,r0
	Exch	; => r3,rv,r2,r1,r0
	Pop $3	; => rv,r2,r1,r0
	Exch 	; => r2,rv,r1,r0
	Pop $2	; => rv,r1,r0
	Exch	; => r1,rv,r0
	Pop $1	; => rv,r0
	Exch	; => r0,rv
	Pop $0	; => rv 
FunctionEnd
 

Function LaunchLink
  Exec "$INSTDIR\pentedb.exe"
FunctionEnd