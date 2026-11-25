-- "Bli online" for cars is switched off (app.catalog.car-online-purchase.enabled=false), so the
-- Makina description must not promise it. Only the text set by V40 is replaced: a description
-- an admin has already edited stays as it is.
UPDATE categories
SET description = 'Zbulo makina të reja dhe të përdorura. Kontakto shitësin për çmimin, provën dhe dokumentet.'
WHERE kind = 'CAR'
  AND description = 'Zbulo makina të reja dhe të përdorura. Kontakto shitësin ose bli online me çmimin e plotë.';
