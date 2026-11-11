; Zynox for Windows — NSIS installer (per-user, no admin needed)
Unicode true
SetCompressor /SOLID lzma
SetCompressorDictSize 32

!include "MUI2.nsh"

Name "Zynox"
OutFile "Zynox-Setup-8.3.0.exe"
InstallDir "$LOCALAPPDATA\Programs\Zynox"
InstallDirRegKey HKCU "Software\Zynox" "InstallDir"
RequestExecutionLevel user

VIProductVersion "8.3.0.0"
VIAddVersionKey "ProductName" "Zynox"
VIAddVersionKey "FileDescription" "Zynox — fast, beautiful video downloader for Windows"
VIAddVersionKey "FileVersion" "8.3.0.0"
VIAddVersionKey "LegalCopyright" "Team Zynox — FCUB CSE"

!define MUI_ICON "assets\icon.ico"
!define MUI_UNICON "assets\icon.ico"
!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Zynox" SecMain
  SetOutPath "$INSTDIR"
  ; Close a running copy so files can be replaced
  nsExec::Exec 'taskkill /IM Zynox.exe /F'
  Pop $0
  File /r "release\Zynox-win32-x64\*.*"

  WriteRegStr HKCU "Software\Zynox" "InstallDir" "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall Zynox.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "DisplayName" "Zynox"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "DisplayVersion" "8.3.0"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "Publisher" "Team Zynox"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "DisplayIcon" "$INSTDIR\resources\app\assets\icon.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "UninstallString" '"$INSTDIR\Uninstall Zynox.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox" "InstallLocation" "$INSTDIR"

  ; zynox:// protocol (the browser extension uses this to launch Zynox)
  WriteRegStr HKCU "Software\Classes\zynox" "" "URL:Zynox Link"
  WriteRegStr HKCU "Software\Classes\zynox" "URL Protocol" ""
  WriteRegStr HKCU "Software\Classes\zynox\DefaultIcon" "" "$INSTDIR\Zynox.exe,0"
  WriteRegStr HKCU "Software\Classes\zynox\shell\open\command" "" '"$INSTDIR\Zynox.exe" "%1"'

  CreateDirectory "$SMPROGRAMS\Zynox"
  CreateShortCut "$SMPROGRAMS\Zynox\Zynox.lnk" "$INSTDIR\Zynox.exe" "" "$INSTDIR\resources\app\assets\icon.ico"
  CreateShortCut "$SMPROGRAMS\Zynox\Uninstall Zynox.lnk" "$INSTDIR\Uninstall Zynox.exe"
  CreateShortCut "$DESKTOP\Zynox.lnk" "$INSTDIR\Zynox.exe" "" "$INSTDIR\resources\app\assets\icon.ico"
SectionEnd

Section "Uninstall"
  nsExec::Exec 'taskkill /IM Zynox.exe /F'
  Pop $0
  Delete "$DESKTOP\Zynox.lnk"
  RMDir /r "$SMPROGRAMS\Zynox"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Zynox"
  DeleteRegKey HKCU "Software\Classes\zynox"
  DeleteRegKey HKCU "Software\Zynox"
  RMDir /r "$INSTDIR"
SectionEnd
