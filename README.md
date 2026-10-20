# Plánovač práce HQS – Android (2.13.44)

Android WebView klient pro stávající server Plánovače práce. **Neobsahuje serverovou databázi ani hesla.**

## Instalace
Po úspěšném workflow otevřete [Actions](https://github.com/ZemanAnt/planovac-prace-android/actions), vyberte nejnovější úspěšný běh **Android APK**, v části **Artifacts** stáhněte **Planovac-Prace-HQS-debug-APK** a ze ZIPu rozbalte `app-debug.apk`.

Při spuštění zadejte adresu svého serveru (HTTPS; HTTP jen v lokální síti). Přihlašování provádí server.

**Pozor:** Debug APK je určeno k internímu testování. Změny oznámení na pozadí a offline dostupnost podléhají možnostem webového klienta; nativní push notifikace zatím nejsou implementovány.
