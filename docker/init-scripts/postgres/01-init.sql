-- ================================================
-- Project03 Backend - PostgreSQL 초기화 스크립트
-- ================================================

-- 서비스별 데이터베이스 생성
CREATE DATABASE auth_db;
CREATE DATABASE user_db;
CREATE DATABASE test_db;
CREATE DATABASE product_db;
CREATE DATABASE stock_db;
CREATE DATABASE funding_db;
CREATE DATABASE sales_db;
CREATE DATABASE hotdeal_db;

-- auth_db 초기 설정
\c auth_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- user_db 초기 설정
\c user_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- test_db 초기 설정 (테스트 서버용)
\c test_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- product_db 초기 설정
\c product_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- stock_db 초기 설정
\c stock_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- funding_db 초기 설정
\c funding_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- sales_db 초기 설정
\c sales_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- hotdeal_db 초기 설정
\c hotdeal_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
