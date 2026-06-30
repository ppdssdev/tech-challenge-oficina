insert into service_catalog_items (id, name, description, base_price, estimated_minutes, active, created_at, updated_at) values
('11111111-1111-1111-1111-111111111111', 'Troca de óleo', 'Substituição de óleo do motor e verificação básica de filtros.', 120.00, 40, true, now(), now()),
('22222222-2222-2222-2222-222222222222', 'Alinhamento', 'Alinhamento de direção.', 150.00, 60, true, now(), now()),
('33333333-3333-3333-3333-333333333333', 'Balanceamento', 'Balanceamento das rodas.', 100.00, 50, true, now(), now()),
('44444444-4444-4444-4444-444444444444', 'Diagnóstico eletrônico', 'Leitura de scanner e análise de falhas.', 180.00, 45, true, now(), now());

insert into parts (id, name, sku, unit_price, quantity_in_stock, minimum_stock, active, created_at, updated_at) values
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Óleo sintético 5W30 1L', 'OIL-5W30-1L', 48.90, 80, 10, true, now(), now()),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Filtro de óleo', 'FILTER-OIL-001', 35.00, 40, 8, true, now(), now()),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Filtro de ar', 'FILTER-AIR-001', 55.00, 25, 5, true, now(), now()),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Pastilha de freio dianteira', 'BRAKE-PAD-FRONT', 220.00, 16, 4, true, now(), now());
