# Elasticsearch 8.10.4 + IK 中文分词器
# IK 版本必须与 ES 版本完全一致；下载源为 IK 官方（infinilabs）发布渠道，
# 8.10.4 地址已实测可访问（HTTP 200）。
FROM docker.elastic.co/elasticsearch/elasticsearch:8.10.4
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.10.4