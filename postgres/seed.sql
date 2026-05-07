-- Seed model_metadata
INSERT INTO model_metadata (model_name, model_type, version, metrics, file_path, is_active, trained_at)
VALUES
  ('RandomForestClassifier', 'classifier', '1.0.0',
   '{"accuracy": 0.94, "f1_score": 0.93, "precision": 0.94, "recall": 0.93}',
   '/app/models/best_classifier.joblib', TRUE, NOW() - INTERVAL '2 days'),
  ('GradientBoostingRegressor', 'regressor', '1.0.0',
   '{"mae": 18.4, "mse": 820.5, "r2": 0.89}',
   '/app/models/best_regressor.joblib', TRUE, NOW() - INTERVAL '2 days'),
  ('IsolationForest', 'anomaly', '1.0.0',
   '{"contamination": 0.05, "n_estimators": 100}',
   '/app/models/anomaly_detector.joblib', TRUE, NOW() - INTERVAL '2 days');

-- Seed training_runs
INSERT INTO training_runs (run_id, status, num_samples, best_classifier, best_regressor, classifier_metrics, regressor_metrics, started_at, completed_at)
VALUES
  ('run-001', 'COMPLETED', 30000, 'RandomForestClassifier', 'GradientBoostingRegressor',
   '{"accuracy": 0.94, "f1_score": 0.93}',
   '{"mae": 18.4, "r2": 0.89}',
   NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '4 minutes'),
  ('run-002', 'COMPLETED', 30000, 'RandomForestClassifier', 'GradientBoostingRegressor',
   '{"accuracy": 0.95, "f1_score": 0.94}',
   '{"mae": 17.1, "r2": 0.91}',
   NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '3 minutes 45 seconds');

-- Seed alerts
INSERT INTO alerts (alert_type, severity, log_data, anomaly_details, acknowledged, created_at)
VALUES
  ('anomaly_detected', 'HIGH',
   '{"method":"GET","path":"/api/users","status_code":503,"response_time_ms":1850}',
   '{"score": -0.42, "contamination": 0.05}',
   FALSE, NOW() - INTERVAL '30 minutes'),
  ('anomaly_detected', 'CRITICAL',
   '{"method":"POST","path":"/api/payments","status_code":500,"response_time_ms":4200}',
   '{"score": -0.71, "contamination": 0.05}',
   FALSE, NOW() - INTERVAL '15 minutes'),
  ('anomaly_detected', 'MEDIUM',
   '{"method":"DELETE","path":"/api/orders","status_code":503,"response_time_ms":960}',
   '{"score": -0.28, "contamination": 0.05}',
   TRUE, NOW() - INTERVAL '1 hour'),
  ('anomaly_detected', 'LOW',
   '{"method":"PUT","path":"/api/settings","status_code":200,"response_time_ms":720}',
   '{"score": -0.19, "contamination": 0.05}',
   TRUE, NOW() - INTERVAL '2 hours');
