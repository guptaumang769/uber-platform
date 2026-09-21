-- Seed ~5 drivers clustered around Bangalore city centre (12.97, 77.59), all AVAILABLE so the
-- matching engine has candidates immediately. Coordinates are a few hundred metres apart.
INSERT INTO drivers (name, email, vehicle, status, current_lat, current_lng) VALUES
  ('Ravi Kumar',   'ravi@example.com',   'Toyota Etios',   'AVAILABLE', 12.9716, 77.5946),
  ('Suresh Babu',  'suresh@example.com', 'Maruti Dzire',   'AVAILABLE', 12.9750, 77.6010),
  ('Meena Iyer',   'meena@example.com',  'Hyundai Aura',   'AVAILABLE', 12.9680, 77.5900),
  ('Arun Prasad',  'arun@example.com',   'Honda Amaze',    'AVAILABLE', 12.9800, 77.5980),
  ('Latha Reddy',  'latha@example.com',  'Tata Tigor',     'AVAILABLE', 12.9650, 77.6050);
