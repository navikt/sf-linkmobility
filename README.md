# sf-linkmobility [<img align="right" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" width="18" alt="GitHub repository" />](https://github.com/navikt/sf-linkmobility)

En liten Kotlin-tjeneste som fungerer som proxy mellom [LINK Mobility](https://www.linkmobility.com/) og Salesforce.

`POST /api/sms` videresender mottatt request body til Salesforces Apex REST-endepunkt `receiveSMS` og returnerer responsen fra Salesforce.
