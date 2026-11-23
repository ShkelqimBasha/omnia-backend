# Backup automatik i databazës

`omnia-db-backup.sh` merr një kopje të verifikuar të databazës MySQL në serverin e production-it, çdo ditë.
Me `--restore-test`, një herë në javë, provon edhe ta rikthejë kopjen në një kontejner MySQL të përkohshëm,
pa rrjet dhe pa porta. Databaza live nuk preket kurrë.

## Çfarë kontrollon

- `mysqldump` me `--single-transaction` (pa bllokuar tabelat);
- gzip i vlefshëm, rreshti i fundit `-- Dump completed`;
- numri i tabelave në kopje = numri i tabelave në databazë;
- versioni i fundit i Flyway;
- sha256 për çdo kopje, rrotullim pas 14 ditësh (`KEEP_DAYS`), ndalon nëse ka më pak se 2 GB vend (`MIN_FREE_MB`);
- testi i restaurimit krahason tabelat, versionin e Flyway dhe numrin e rreshtave për `users`, `products`, `orders`.

## Instalimi (bëhet me dorë nga pronari, NUK është instaluar)

```bash
sudo install -o deploy -g deploy -m 750 omnia-db-backup.sh /opt/omnia/bin/omnia-db-backup.sh
crontab -e   # si përdoruesi deploy
```

```cron
# çdo ditë në 03:15 UTC; çdo të diel me provë restaurimi
15 3 * * 1-6 /opt/omnia/bin/omnia-db-backup.sh                >> /home/deploy/backups/daily/backup.log 2>&1
15 3 * * 0   /opt/omnia/bin/omnia-db-backup.sh --restore-test >> /home/deploy/backups/daily/backup.log 2>&1
```

Kopjet mbeten në të njëjtin server. Për mbrojtje të vërtetë kopjoji edhe jashtë serverit
(p.sh. `rsync` drejt një makine tjetër ose ruajtje objektesh), siç bëjnë skriptet e deploy-t.

## Kontrolli

```bash
cat /home/deploy/backups/daily/LAST_BACKUP /home/deploy/backups/daily/LAST_RESTORE_TEST
tail -n 30 /home/deploy/backups/daily/backup.log
```
