Var td018UserAppData
Var td018PersistentDir
Var td018CandidateDir
Var td018MigrationError
Var td018HklmInstallDir
Var td018HkcuInstallDir
Var td018StageDir
Var td018SourceLabel

!ifndef BUILD_UNINSTALLER
Function td018Trace
  Exch $0
  Push $1
  FileOpen $1 "$TEMP\Budcom-TD018-installer.log" a
  IfErrors td018_trace_done
  FileWrite $1 "$0$\r$\n"
  FileClose $1
  td018_trace_done:
    Pop $1
    Pop $0
FunctionEnd

!macro td018Trace MESSAGE
  Push "${MESSAGE}"
  Call td018Trace
!macroend
!endif

!ifndef BUILD_UNINSTALLER
Function td018ConsiderLegacyRoot
  Exch $0
  StrCmp $0 "" td018_consider_empty

  StrCpy $1 "$0\resources\connector\dist\data\transport"
  IfFileExists "$1\transport-key.pem" td018_consider_key_present td018_consider_key_absent

  td018_consider_key_present:
    IfFileExists "$1\transport-cert.pem" td018_consider_complete td018_consider_partial

  td018_consider_key_absent:
    IfFileExists "$1\transport-cert.pem" td018_consider_partial td018_consider_absent

  td018_consider_partial:
    !insertmacro td018Trace "source_$td018SourceLabel=partial"
    StrCpy $td018MigrationError "A legacy Budcom transport identity is incomplete. Installation stopped before replacing the existing application."
    Goto td018_consider_done

  td018_consider_complete:
    !insertmacro td018Trace "source_$td018SourceLabel=complete"
    StrCmp $td018CandidateDir "" td018_consider_first
    nsExec::ExecToStack '"$SYSDIR\fc.exe" /B "$td018CandidateDir\transport-key.pem" "$1\transport-key.pem"'
    Pop $2
    Pop $3
    !insertmacro td018Trace "compare_existing_key_exit=$2"
    StrCmp $2 "0" 0 td018_consider_conflict
    nsExec::ExecToStack '"$SYSDIR\fc.exe" /B "$td018CandidateDir\transport-cert.pem" "$1\transport-cert.pem"'
    Pop $2
    Pop $3
    !insertmacro td018Trace "compare_existing_cert_exit=$2"
    StrCmp $2 "0" td018_consider_done td018_consider_conflict

  td018_consider_first:
    StrCpy $td018CandidateDir $1
    !insertmacro td018Trace "candidate_selected=$td018SourceLabel"
    Goto td018_consider_done

  td018_consider_conflict:
    !insertmacro td018Trace "source_conflict=$td018SourceLabel"
    StrCpy $td018MigrationError "Different legacy Budcom transport identities were found. Installation stopped to preserve secure pairing."
    Goto td018_consider_done

  td018_consider_empty:
  td018_consider_absent:
    !insertmacro td018Trace "source_$td018SourceLabel=absent"

  td018_consider_done:
    Pop $0
FunctionEnd
!endif

!macro preInit
  !ifndef BUILD_UNINSTALLER
    ; Capture the interactive user's roaming AppData before initMultiUser switches shell context.
    Delete "$TEMP\Budcom-TD018-installer.log"
    StrCpy $td018UserAppData "$APPDATA"
    !insertmacro td018Trace "preInit=complete"
  !endif
!macroend

!macro customInit
  ; Runs after initMultiUser resolves installation context/path, and before uninstallOldVersion.
  !insertmacro td018Trace "customInit=entered"
  StrCpy $td018PersistentDir "$td018UserAppData\@budcom\desktop\connector-transport-identity"
  StrCpy $td018CandidateDir ""
  StrCpy $td018MigrationError ""

  ; A complete persistent pair is authoritative and is never overwritten.
  IfFileExists "$td018PersistentDir\transport-key.pem" td018_destination_key_present td018_destination_key_absent

  td018_destination_key_present:
    !insertmacro td018Trace "destination_key=present"
    IfFileExists "$td018PersistentDir\transport-cert.pem" td018_done td018_destination_partial

  td018_destination_key_absent:
    IfFileExists "$td018PersistentDir\transport-cert.pem" td018_destination_partial td018_find_sources

  td018_destination_partial:
    !insertmacro td018Trace "destination=partial_abort"
    Abort "The persistent Budcom transport identity is incomplete. Installation stopped without replacing the existing application."

  td018_find_sources:
    !insertmacro td018Trace "destination=empty"
    ; Inspect resolved, per-machine, and per-user roots. Duplicate identical pairs are safe.
    StrCpy $td018SourceLabel "resolved"
    Push "$INSTDIR"
    Call td018ConsiderLegacyRoot
    ReadRegStr $td018HklmInstallDir HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation
    StrCpy $td018SourceLabel "hklm"
    Push "$td018HklmInstallDir"
    Call td018ConsiderLegacyRoot
    ReadRegStr $td018HkcuInstallDir HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation
    StrCpy $td018SourceLabel "hkcu"
    Push "$td018HkcuInstallDir"
    Call td018ConsiderLegacyRoot

    StrCmp $td018MigrationError "" 0 td018_abort
    StrCmp $td018CandidateDir "" td018_no_candidate

    ; Stage both files under the same AppData parent, verify exact bytes, then publish by directory rename.
    System::Call 'kernel32::GetCurrentProcessId()i.r0'
    StrCpy $td018StageDir "$td018UserAppData\@budcom\desktop\connector-transport-identity.migrating-$0"
    IfFileExists "$td018StageDir" td018_stage_collision
    !insertmacro td018Trace "stage=create"
    CreateDirectory "$td018StageDir"
    IfErrors td018_copy_failed
    ClearErrors
    CopyFiles /SILENT "$td018CandidateDir\transport-key.pem" "$td018StageDir\transport-key.pem"
    IfErrors td018_copy_failed
    CopyFiles /SILENT "$td018CandidateDir\transport-cert.pem" "$td018StageDir\transport-cert.pem"
    IfErrors td018_copy_failed
    nsExec::ExecToStack '"$SYSDIR\fc.exe" /B "$td018CandidateDir\transport-key.pem" "$td018StageDir\transport-key.pem"'
    Pop $4
    Pop $5
    !insertmacro td018Trace "verify_staged_key_exit=$4"
    StrCmp $4 "0" 0 td018_copy_failed
    nsExec::ExecToStack '"$SYSDIR\fc.exe" /B "$td018CandidateDir\transport-cert.pem" "$td018StageDir\transport-cert.pem"'
    Pop $4
    Pop $5
    !insertmacro td018Trace "verify_staged_cert_exit=$4"
    StrCmp $4 "0" 0 td018_copy_failed
    !insertmacro td018Trace "stage=verified"

    ; Recheck destination immediately before atomic publication. RMDir succeeds only if it is empty.
    IfFileExists "$td018PersistentDir\transport-key.pem" td018_destination_changed
    IfFileExists "$td018PersistentDir\transport-cert.pem" td018_destination_changed
    IfFileExists "$td018PersistentDir" 0 td018_publish
      ClearErrors
      RMDir "$td018PersistentDir"
      IfErrors td018_destination_changed
    td018_publish:
    ClearErrors
    Rename "$td018StageDir" "$td018PersistentDir"
    IfErrors td018_publish_failed
    !insertmacro td018Trace "publish=complete"
    Goto td018_done

  td018_stage_collision:
    !insertmacro td018Trace "abort=stage_collision"
    Abort "A Budcom identity migration staging path already exists. Installation stopped without replacing the existing application."

  td018_destination_changed:
    !insertmacro td018Trace "abort=destination_changed"
    RMDir /r "$td018StageDir"
    Abort "The persistent Budcom transport identity changed during installation. Installation stopped without replacing the existing application."

  td018_copy_failed:
    !insertmacro td018Trace "abort=copy_or_verify_failed"
    RMDir /r "$td018StageDir"
    Abort "The legacy Budcom transport identity could not be copied and verified. Installation stopped without replacing the existing application."

  td018_publish_failed:
    !insertmacro td018Trace "abort=publish_failed"
    RMDir /r "$td018StageDir"
    Abort "The preserved Budcom transport identity could not be published atomically. Installation stopped without replacing the existing application."

  td018_abort:
    !insertmacro td018Trace "abort=source_state"
    Abort "$td018MigrationError"

  td018_no_candidate:
    !insertmacro td018Trace "migration=no_candidate"

  td018_done:
    !insertmacro td018Trace "customInit=complete"
!macroend

!macro customInstall
  ; Replace rules by stable name so upgrades and install-path changes cannot leave a stale executable target.
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector HTTP"'
  Pop $0
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector HTTPS"'
  Pop $0
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector mDNS"'
  Pop $0
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall add rule name="Budcom Connector HTTP" dir=in action=allow program="$INSTDIR\resources\node\node.exe" enable=yes profile=private,public protocol=TCP localport=8080 remoteip=LocalSubnet'
  Pop $0
  StrCmp $0 "0" +2
    Abort "Unable to create the Budcom Connector HTTP firewall rule."
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall add rule name="Budcom Connector HTTPS" dir=in action=allow program="$INSTDIR\resources\node\node.exe" enable=yes profile=private,public protocol=TCP localport=8443 remoteip=LocalSubnet'
  Pop $0
  StrCmp $0 "0" +2
    Abort "Unable to create the Budcom Connector HTTPS firewall rule."
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall add rule name="Budcom Connector mDNS" dir=in action=allow program="$INSTDIR\resources\node\node.exe" enable=yes profile=private,public protocol=UDP localport=5353 remoteip=LocalSubnet'
  Pop $0
  StrCmp $0 "0" +2
    Abort "Unable to create the Budcom Connector mDNS firewall rule."
!macroend

!macro customUnInstall
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector HTTP"'
  Pop $0
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector HTTPS"'
  Pop $0
  nsExec::ExecToLog '"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Budcom Connector mDNS"'
  Pop $0
!macroend
