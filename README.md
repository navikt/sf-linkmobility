# sf-linkmobility# sf-linkmobility

En liten Kotlin-tjeneste som fungerer som proxy mellom [LINK Mobility](https://www.linkmobility.com/) og Salesforce.

`POST /api/sms` videresender mottatt request body til Salesforces Apex REST-endepunkt `receiveSMS` og returnerer responsen fra Salesforce.
