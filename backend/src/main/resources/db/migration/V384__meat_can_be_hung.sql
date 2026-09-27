-- #37 — "hang the meat" reached nothing.
--
-- Act eight. dry_meat is how meat is kept, and it answers to "dry the meat", "rack the meat" and "jerk the
-- meat" -- every way of saying it except the one most people would use. Hanging meat IS racking it; the word is
-- just older and commoner, and it was the only one missing.
UPDATE material_process SET keywords = keywords || ',hang the meat,hang meat,hang it to dry'
 WHERE process_key = 'dry_meat'
   AND ',' || keywords || ',' NOT LIKE '%,hang the meat,%';

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'dry_meat' AND ',' || keywords || ',' LIKE '%,hang the meat,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V384: the meat still cannot be hung'; END IF;

    -- And it must still answer to the phrasings it already had, or this traded one word for another.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'dry_meat' AND ',' || keywords || ',' LIKE '%,rack the meat,%'
       AND ',' || keywords || ',' LIKE '%,dry the meat,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V384: drying meat has lost a phrasing it had'; END IF;
END $$;
