Stations API für DB Systel GmbH

# Bauen

Siehe [Buildfile](Buildfile).
Hier wird auch der GTFS-Datensatz heruntergeladen. Bitte auf einen eigenen Link anpassen.

# Starten

Siehe [Procfile](Procfile).

# Konfigurieren

Siehe [meetingstation.yml](meetingstation.yml). Bitte insbesondere den Dateinamen `gtfsFile` an das anpassen, was 
in [Buildfile](Buildfile) heruntergeladen wird.

Getestet auf Amazon Elastic Beanstalk, wo es ohne weitere Änderungen oder Einstellungen läuft.
Der Dienst profitiert auch bei geringer Auslastung davon, mit Lastverteilung auf mehreren Instanzen zu laufen (z.B. 2*t2.small),
da der Client zur Abarbeitung jeder Anfrage mehrere Anfragen gleichzeitig an diesen Dienst schickt.

Die Einstellung `server.maxThreads` in [meetingstation.yml](meetingstation.yml) sollte nicht viel größer als 2.5*(Anzahl Kerne) sein,
da der Dienst rechenzeitgebunden ist. Zu viele Threads erhöhen daher nur den Speicherverbrauch, ohne dass es schneller wird. Eigentlich 
eher 1*(Anzahl Kerne), aber das Web-Framework zählt hier anscheinend irgendwelche Hilfs-Threads mit.
