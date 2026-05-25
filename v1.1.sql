-- 排序算法配置默认值
INSERT INTO site_config (config_key, config_value) VALUES ('rank.recommend.weight.like', '2.0');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.recommend.weight.comment', '3.0');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.recommend.weight.view', '0.05');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.recommend.decay.base', '2');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.recommend.decay.exponent', '1.2');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.hot.weight.view', '0.1');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.hot.weight.like', '1.0');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.hot.weight.comment', '2.0');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.hot.decay.base', '1');
INSERT INTO site_config (config_key, config_value) VALUES ('rank.hot.decay.exponent', '1.5');
