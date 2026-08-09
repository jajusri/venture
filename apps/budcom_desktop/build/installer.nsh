!macro preInit
  ; TD-018 upgrade bridge: preserve the old install-tree identity before NSIS replaces resources.
  ; Never overwrite a persistent or partial identity; those states remain authoritative/fail-safe.
  IfFileExists "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem" td018_done
  IfFileExists "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem" td018_done
  IfFileExists "$INSTDIR\resources\connector\dist\data\transport\transport-key.pem" 0 td018_done
  IfFileExists "$INSTDIR\resources\connector\dist\data\transport\transport-cert.pem" 0 td018_done
  CreateDirectory "$APPDATA\@budcom\desktop\connector-transport-identity"
  CopyFiles /SILENT "$INSTDIR\resources\connector\dist\data\transport\transport-key.pem" "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem.migrating"
  CopyFiles /SILENT "$INSTDIR\resources\connector\dist\data\transport\transport-cert.pem" "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem.migrating"
  IfFileExists "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem.migrating" 0 td018_copy_failed
  IfFileExists "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem.migrating" 0 td018_copy_failed
  Rename "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem.migrating" "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem"
  Rename "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem.migrating" "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem"
  Goto td018_done
  td018_copy_failed:
  Delete "$APPDATA\@budcom\desktop\connector-transport-identity\transport-key.pem.migrating"
  Delete "$APPDATA\@budcom\desktop\connector-transport-identity\transport-cert.pem.migrating"
  td018_done:
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
