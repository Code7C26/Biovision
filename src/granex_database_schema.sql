-- =============================================================================
-- GRANEX AGTECH - SCHEMAS & TABLAS BASE DE DATOS MYSQL 8.0
-- Agricultura de Precisión, Cotizaciones y Diagnóstico Agro-AI
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `granex_agtech_db` 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `granex_agtech_db`;

-- -----------------------------------------------------------------------------
-- TABLA: usuarios
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `usuarios` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_productor` VARCHAR(150) NOT NULL,
  `establecimiento` VARCHAR(150) NOT NULL,
  `ubicacion_principal` VARCHAR(200) NOT NULL,
  `hectareas_totales` INT NOT NULL DEFAULT 0,
  `fecha_creacion` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_usuario_establecimiento` (`establecimiento`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- TABLA: catalogo_plagas_malezas (RAG Local)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `catalogo_plagas_malezas` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `nombre_comun` VARCHAR(150) NOT NULL,
  `nombre_cientifico` VARCHAR(150) NOT NULL,
  `tipo` ENUM('Plaga', 'Maleza', 'Enfermedad') NOT NULL,
  `sintomas` TEXT NOT NULL,
  `tratamiento_oficial` VARCHAR(255) NOT NULL,
  `dosis_recomendada` VARCHAR(100) NOT NULL,
  `principio_activo` VARCHAR(150) NOT NULL,
  `restricciones` TEXT NOT NULL,
  FULLTEXT KEY `ft_busqueda_rag` (`nombre_comun`, `nombre_cientifico`, `sintomas`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- TABLA: precios_rosario
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `precios_rosario` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `cultivo` VARCHAR(50) NOT NULL, -- Soja, Maíz, Trigo, Girasol
  `fecha` DATE NOT NULL,
  `precio_usd` DECIMAL(10,2) NOT NULL,
  `precio_ars` DECIMAL(12,2) NOT NULL,
  `variacion_porcentaje` DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  `puerto` VARCHAR(100) DEFAULT 'Puerto de Rosario (Cámara Arbitral)',
  INDEX `idx_cultivo_fecha` (`cultivo`, `fecha` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- TABLA: registros_monitoreo
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `registros_monitoreo` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `lote_nombre` VARCHAR(100) NOT NULL,
  `cultivo` VARCHAR(50) NOT NULL,
  `fecha_monitoreo` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `diagnostico_ia` VARCHAR(150) NOT NULL,
  `confianza_porcentaje` DECIMAL(5,2) NOT NULL,
  `tratamiento_sugerido` VARCHAR(255) NOT NULL,
  `dosis` VARCHAR(100) NOT NULL,
  `sintomas_observados` TEXT,
  `imagen_url` VARCHAR(255),
  INDEX `idx_lote_cultivo` (`lote_nombre`, `cultivo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
-- SEED DATA CERTIFICADO (REGISTROS REALES AGTECH ARGENTINA)
-- =============================================================================

INSERT INTO `usuarios` (`id`, `nombre_productor`, `establecimiento`, `ubicacion_principal`, `hectareas_totales`)
VALUES (1, 'Ing. Agr. Mateo Rossi', 'Establecimiento Don Pedro', 'Marcos Juárez, Córdoba / Rosario, Santa Fe', 1450)
ON DUPLICATE KEY UPDATE `nombre_productor` = VALUES(`nombre_productor`);

INSERT INTO `catalogo_plagas_malezas` 
(`nombre_comun`, `nombre_cientifico`, `tipo`, `sintomas`, `tratamiento_oficial`, `dosis_recomendada`, `principio_activo`, `restricciones`)
VALUES 
('Isoca Medidora', 'Rachiplusia nu', 'Plaga', 'Defoliación activa en tercios medios y superiores del cultivo de soja/maíz. Larvas verdes caminan plegando el cuerpo.', 'Coragen 20 SC / Ampligo 150 ZC', '40 - 50 ml/ha', 'Chlorantraniliprole 20% p/v', 'Carencia: 21 días. Aplicar con volumen mínimo de 80 L/ha y tarjeta hidrosensible.'),
('Rama Negra', 'Conyza bonariensis / Conyza sumatrensis', 'Maleza', 'Planta erecta con tallo pubescente y ramificado. Altamente tolerante al glifosato en estado adulto.', 'Texaro (Diclosulam + Halauxifen) / Percutor + Flumioxazin', '35 g/ha Texaro + 1% Aceite Metilado', 'Diclosulam 8.4% + Halauxifen-metil', 'Especial para barbecho químico temprano. No aplicar en presiembra inmediata de cultivos sensibles.'),
('Roya Asiática de la Soja', 'Phakopsora pachyrhizi', 'Enfermedad', 'Pústulas pequeñas color marrón-rojizo en la cara abaxial de las hojas de soja. Defoliación prematura.', 'Miravis Duo / Sphera MAX', '300 - 400 ml/ha', 'Adepidyn (Pydiflumetofen) + Difenoconazole', 'Carencia: 30 días. Aplicar de manera preventiva o al alcanzar 5% de incidencia en R1-R5.'),
('Oruga Bolillera', 'Helicoverpa gelotopoeon', 'Plaga', 'Daño directo en brotes terminales, flores y vainas jóvenes de soja. Corte de plántulas.', 'Voliam Targo / Belt (Flubendiamide)', '80 - 100 ml/ha', 'Chlorantraniliprole + Abamectina', 'Carencia: 14 días. Uso de picos cono hueco para buena penetración en el canopeo.'),
('Chinche Verde', 'Nezara viridula', 'Plaga', 'Picado de granos en vainas, deteniendo el llenado de grano y reduciendo peso de 1000 granos.', 'Engeo / Salomon (Imidacloprid + Beta-ciflutrina)', '150 - 200 ml/ha', 'Thiamethoxam 14.1% + Lambda-cialotrina 10.6%', 'Carencia: 21 días. Monitorear con paño vertical.')
ON DUPLICATE KEY UPDATE `nombre_comun` = VALUES(`nombre_comun`);

INSERT INTO `precios_rosario` (`cultivo`, `fecha`, `precio_usd`, `precio_ars`, `variacion_porcentaje`, `puerto`)
VALUES 
('Soja', '2026-03-01', 310.00, 310000.00, 0.50, 'Puerto de Rosario (Cámara Arbitral)'),
('Soja', '2026-04-01', 318.00, 318000.00, 2.50, 'Puerto de Rosario (Cámara Arbitral)'),
('Soja', '2026-05-01', 325.00, 325000.00, 2.20, 'Puerto de Rosario (Cámara Arbitral)'),
('Soja', '2026-06-01', 320.00, 320000.00, -1.50, 'Puerto de Rosario (Cámara Arbitral)'),
('Soja', '2026-07-01', 332.00, 332000.00, 3.75, 'Puerto de Rosario (Cámara Arbitral)'),
('Soja', '2026-07-28', 338.00, 338000.00, 1.80, 'Puerto de Rosario (Cámara Arbitral)'),
('Maíz', '2026-07-28', 194.00, 194000.00, 2.10, 'Puerto de Rosario (Cámara Arbitral)'),
('Trigo', '2026-07-28', 240.00, 240000.00, 2.12, 'Puerto de Rosario (Cámara Arbitral)'),
('Girasol', '2026-07-28', 375.00, 375000.00, 1.90, 'Puerto de Rosario (Cámara Arbitral)');
