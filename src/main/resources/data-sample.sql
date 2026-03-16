-- Sample data for fixture mode
-- This script can be used to populate the database with example data
-- Run this after the application has created the schema

-- Insert sample backend configurations
INSERT INTO backend_config (id, name, base_url, path, security_type, security_config, enabled, created_at, updated_at)
VALUES 
    (1, 'user-service', 'http://localhost:9001', '/api/v1/users', 'JWT', '{}', true, NOW(), NOW()),
    (2, 'order-service', 'http://localhost:9002', '/api/v1/orders', 'API_KEY', '{"headerName": "X-API-Key"}', true, NOW(), NOW()),
    (3, 'payment-service', 'http://localhost:9003', '/api/v1/payments', 'OAUTH2', '{}', true, NOW(), NOW());

-- Insert mock endpoints for user-service
INSERT INTO mock_endpoint (id, backend_name, method, path, description, open_api_schema, enabled, created_at, updated_at)
VALUES
    (1, 'user-service', 'GET', '/users', 'Get all users', NULL, true, NOW(), NOW()),
    (2, 'user-service', 'GET', '/users/{id}', 'Get user by ID', NULL, true, NOW(), NOW()),
    (3, 'user-service', 'POST', '/users', 'Create new user', NULL, true, NOW(), NOW()),
    (4, 'user-service', 'PUT', '/users/{id}', 'Update user', NULL, true, NOW(), NOW()),
    (5, 'user-service', 'DELETE', '/users/{id}', 'Delete user', NULL, true, NOW(), NOW());

-- Insert mock endpoints for order-service
INSERT INTO mock_endpoint (id, backend_name, method, path, description, open_api_schema, enabled, created_at, updated_at)
VALUES
    (6, 'order-service', 'GET', '/orders', 'Get all orders', NULL, true, NOW(), NOW()),
    (7, 'order-service', 'GET', '/orders/{id}', 'Get order by ID', NULL, true, NOW(), NOW()),
    (8, 'order-service', 'POST', '/orders', 'Create new order', NULL, true, NOW(), NOW());

-- Insert mock responses for GET /users
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (1, 1, 'Success - All users', '{}', 200, 
     '[{"id":1,"name":"John Doe","email":"john@example.com","role":"admin"},{"id":2,"name":"Jane Smith","email":"jane@example.com","role":"user"}]',
     '{"Content-Type":"application/json","X-Total-Count":"2"}', 10, true, 100, NOW(), NOW());

-- Insert mock responses for GET /users/{id}
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (2, 2, 'Success - User found', '{}', 200,
     '{"id":1,"name":"John Doe","email":"john@example.com","role":"admin","createdAt":"2024-01-01T00:00:00Z"}',
     '{"Content-Type":"application/json"}', 10, true, 50, NOW(), NOW()),
    (3, 2, 'Error - User not found', '{}', 404,
     '{"error":"User not found","message":"The requested user does not exist"}',
     '{"Content-Type":"application/json"}', 5, true, 50, NOW(), NOW());

-- Insert mock responses for POST /users
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (4, 3, 'Success - User created', '{}', 201,
     '{"id":3,"name":"New User","email":"new@example.com","role":"user","createdAt":"2024-01-15T10:30:00Z"}',
     '{"Content-Type":"application/json","Location":"/api/v1/users/3"}', 10, true, 150, NOW(), NOW()),
    (5, 3, 'Error - Validation failed', '{"bodyContains":"invalid"}', 400,
     '{"error":"Validation error","message":"Invalid user data provided","fields":["email"]}',
     '{"Content-Type":"application/json"}', 20, true, 50, NOW(), NOW());

-- Insert mock responses for PUT /users/{id}
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (6, 4, 'Success - User updated', '{}', 200,
     '{"id":1,"name":"John Doe Updated","email":"john.updated@example.com","role":"admin","updatedAt":"2024-01-15T12:00:00Z"}',
     '{"Content-Type":"application/json"}', 10, true, 100, NOW(), NOW());

-- Insert mock responses for DELETE /users/{id}
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (7, 5, 'Success - User deleted', '{}', 204, '', '{}', 10, true, 75, NOW(), NOW());

-- Insert mock responses for GET /orders
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (8, 6, 'Success - All orders', '{}', 200,
     '[{"id":1001,"userId":1,"amount":99.99,"status":"completed","createdAt":"2024-01-10T09:00:00Z"},{"id":1002,"userId":2,"amount":149.99,"status":"pending","createdAt":"2024-01-12T14:30:00Z"}]',
     '{"Content-Type":"application/json","X-Total-Count":"2"}', 10, true, 120, NOW(), NOW()),
    (9, 6, 'Success - Filtered by status', '{"queryParams":{"status":"pending"}}', 200,
     '[{"id":1002,"userId":2,"amount":149.99,"status":"pending","createdAt":"2024-01-12T14:30:00Z"}]',
     '{"Content-Type":"application/json","X-Total-Count":"1"}', 20, true, 100, NOW(), NOW());

-- Insert mock responses for GET /orders/{id}
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (10, 7, 'Success - Order found', '{}', 200,
     '{"id":1001,"userId":1,"amount":99.99,"status":"completed","items":[{"productId":1,"quantity":2,"price":49.99}],"createdAt":"2024-01-10T09:00:00Z"}',
     '{"Content-Type":"application/json"}', 10, true, 80, NOW(), NOW());

-- Insert mock responses for POST /orders
INSERT INTO mock_response (id, mock_endpoint_id, name, match_conditions, http_status, response_body, response_headers, priority, enabled, delay_ms, created_at, updated_at)
VALUES
    (11, 8, 'Success - Order created', '{}', 201,
     '{"id":1003,"userId":1,"amount":299.99,"status":"pending","createdAt":"2024-01-15T15:00:00Z"}',
     '{"Content-Type":"application/json","Location":"/api/v1/orders/1003"}', 10, true, 200, NOW(), NOW());

-- Reset sequences (PostgreSQL)
-- Note: Adjust starting values as needed
SELECT setval('backend_config_id_seq', (SELECT MAX(id) FROM backend_config));
SELECT setval('mock_endpoint_id_seq', (SELECT MAX(id) FROM mock_endpoint));
SELECT setval('mock_response_id_seq', (SELECT MAX(id) FROM mock_response));
