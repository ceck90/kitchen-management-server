-- trigger function

-- DROP FUNCTION public.menu_items_notify();

-- N.B.: nessun filtro per categoria: tutte le righe vengono notificate e salvate in orders_ack.
-- E' il server cucina a scartare gli articoli delle categorie non importate da GSG (tipologie visibili).
CREATE OR REPLACE FUNCTION public.menu_items_notify()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare
	json_result json;
	v_txt text;
	id INT;
    order_number INT;
    table_number VARCHAR;
    insert_date VARCHAR;
    insert_time VARCHAR;
    client_name VARCHAR;
    take_away BOOLEAN;
    order_notes VARCHAR;
    quantity INT;
    menu_item_id INT;
    menu_item_name VARCHAR;
    menu_item_notes VARCHAR;
    category_id INT;
    category_name VARCHAR;
BEGIN
	RAISE NOTICE 'Nuova riga inserita nella tabella righe_articoli con id: %', NEW.id;

		select json_build_object(
        'id', ra.id,
        'orderNumber', o.progressivo, 'tableNumber', o."numeroTavolo", 'date', o."data", 'time', o.ora, 'clientName', o.cliente,
        'takeAway', o.esportazione, 'orderNotes', o.note,
        'quantity', r.quantita,
        'menuItemId', a.id, 'menuItemName', a.descrizione, 'menuItemNotes', ra.note,
        'categoryId', t.id, 'categoryName', t.descrizione
        )
        into json_result
        from righe_articoli ra
        inner join righe r on r.id = ra.id_riga
        inner join articoli a on r.descrizionebreve = a.descrizionebreve
        inner join tipologie t on a.id_tipologia = t.id
        inner join ordini o on o.id = r.id_ordine
        where ra.id = new.id;

	-- riga senza articolo corrispondente (join vuota): non bloccare l'inserimento dell'ordine su GSG
	if json_result is null then
		RAISE NOTICE 'Riga articolo % senza articolo/tipologia associati, notifica non inviata', NEW.id;
		RETURN NEW;
	end if;

	  	-- RAISE NOTICE 'Risultato JSON %', json_result;

	   	v_txt := format('{"operation": "%s","item": %s }', TG_OP, json_result);
	  	-- RAISE NOTICE 'Payload notifica %', v_txt;

	    -- Recupero dei singoli campi dal JSON
        id := (json_result->>'id')::INT;
        order_number := (json_result->>'orderNumber')::INT;
        table_number := json_result->>'tableNumber';
        insert_date := (json_result->>'date');
        insert_time := (json_result->>'time');
        client_name := json_result->>'clientName';
        take_away := (json_result->>'takeAway')::BOOLEAN;
        order_notes := json_result->>'orderNotes';
        quantity := (json_result->>'quantity')::INT;
        menu_item_id := (json_result->>'menuItemId')::INT;
        menu_item_name := json_result->>'menuItemName';
        menu_item_notes := json_result->>'menuItemNotes';
        category_id := (json_result->>'categoryId')::INT;
        category_name := json_result->>'categoryName';

	  	-- insert body notifica in tabella orders_ack
        INSERT INTO orders_ack (
            id, order_number, table_number, insert_date, insert_time, client_name, take_away,
            order_notes, quantity, menu_item_id, menu_item_name, menu_item_notes, category_id, category_name, ack
        ) VALUES (
            id, order_number, table_number, insert_date, insert_time, client_name, take_away,
            order_notes, quantity, menu_item_id, menu_item_name, menu_item_notes, category_id, category_name, false
        );

        -- invio notifica
	    EXECUTE FORMAT('NOTIFY plate_orders, ''%s''', v_txt);

	RETURN NEW;
END;
$function$
;

-- trigger
--DROP TRIGGER righe_articoli_trigger ON public.righe_articoli;

CREATE TRIGGER righe_articoli_trigger
after INSERT OR UPDATE ON righe_articoli
FOR EACH ROW
EXECUTE PROCEDURE menu_items_notify();

-- tabella da usare per verifica ACK notifiche
-- drop table orders_ack
CREATE TABLE orders_ack (
    id int PRIMARY KEY,
    order_number int not null,
    table_number VARCHAR(255),
    insert_date VARCHAR(255) not null,
    insert_time VARCHAR(255) not null,
    client_name varchar(255),
    take_away bool,
    order_notes varchar(255),
    quantity int not null,
    menu_item_id int not null,
    menu_item_name varchar(255),
    menu_item_notes varchar(255),
    category_id int not null,
    category_name varchar(255),
    ack bool
);

CREATE FUNCTION notify_new_order()
	RETURNS TRIGGER AS $$
	BEGIN
		PERFORM pg_notify('new_order', json_build_object(
			'operation', TG_OP,
			'item', row_to_json(NEW)
		)::text);
		RETURN NEW;
	END;
	$$ LANGUAGE plpgsql;

CREATE TRIGGER ordini_trigger
AFTER INSERT ON ordini
FOR EACH ROW EXECUTE FUNCTION notify_new_order();
