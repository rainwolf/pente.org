; Java Launcher
;--------------
 
;You want to change the next four lines
Name "Pente Db"
Caption "Pente Db"
Icon "C:\dsg_src\images\logo.ico"
OutFile "pentedb.exe"
 
SilentInstall silent
AutoCloseWindow true
ShowInstDetails nevershow
 
;You want to change the next two lines too
!define CLASSPATH ".;db2.jar;log4j-1.2.8.jar;derby.jar;swingx-0.9.1.jar"
!define CLASS "org.pente.gameDatabase.swing.Main"
 
Section ""
  Call GetJRE
  Pop $R0
 
  ;MessageBox MB_OK "$R0"
  ; change for your purpose (-jar etc.)
  StrCpy $0 '"$R0" -classpath "${CLASSPATH}" -Djava.library.path=. -Dderby.storage.pageCacheSize=20000 -Xmx200M -Dhost=pente.org -splash:splash.png ${CLASS} log4j.properties db log'
  
 
  SetOutPath $EXEDIR
  Exec $0
SectionEnd
 
Function GetJRE
;
;  Find JRE (javaw.exe)
;  1 - in .\jre directory (JRE Installed with application)
;  2 - in JAVA_HOME environment variable
;  3 - in the registry
;  4 - assume javaw.exe in current dir or PATH
 
  Push $R0
  Push $R1
 
  ClearErrors
  StrCpy $R0 "$EXEDIR\jre\bin\javaw.exe"
  IfFileExists $R0 JreFound
  StrCpy $R0 ""
 
  ClearErrors
  ReadEnvStr $R0 "JAVA_HOME"
  StrCpy $R0 "$R0\bin\javaw.exe"
  IfFileExists $R0 JreFound
  IfErrors 0 JreFound
 
  ClearErrors
  ReadRegStr $R1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  ReadRegStr $R0 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$R1" "JavaHome"
  StrCpy $R0 "$R0\bin\javaw.exe"
  IfFileExists $R0 JreFound
 
  IfErrors 0 JreFound
  StrCpy $R0 "javaw.exe"
        
 JreFound:
  Pop $R1
  Exch $R0
FunctionEnd