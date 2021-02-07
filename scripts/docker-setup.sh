
exit

#
# ##################################################################################################
#
# DOCKER
#
# ##################################################################################################
#
docker-compose up -d
docker exec -it number6_cassandra_1 bash
docker ps [-a]

#
# ##################################################################################################
#
# CASSANDRA
#
# ##################################################################################################
#
# Create the tables
docker exec -i number6_cassandra_1 cqlsh -t < ddl-scripts/create_tables.cql

# Login into the container and starting the Cassandra CLI
docker exec -it number6_cassandra_1 bash
cqlsh

# Display the available KEYSPACES
describe keyspaces;
select * from system_schema.keyspaces;

# Display all the available TABLES
describe tables;

# Display the available tables for a specific keyspace
use <keyspace>;
describe tables;

#
# ##################################################################################################
#
# MONGODB
#
# ##################################################################################################
#
# https://pierrepironin.github.io/docker-et-mongodb/#Lancer-un-client-Mongo-pour-creer-un-super-admin
#
sudo mkdir -p /opt/mongodb/db
docker run -p 27017:27017 -v /opt/mongodb/db:/data/db --name my-mongo-dev -d mongo mongod --auth
docker exec -it my-mongo-dev mongo

db.createUser({
 user:"number6",
 pwd:"number6",
 roles:[
  {
     role:"dbOwner",
     db:"scraping"
  }
 ],
 mechanisms:[
  "SCRAM-SHA-1"
 ]
})

mongo localhost -u number6 -p number6 --authenticationDatabase scraping
user scraping
db.batch.insert({
  batchID: "b1",
  batchSpecification: {
    batchSpecificationID: "bs1",
    auctions: [
      {
        auctionID: "a1",
        url: "url1",
        matched: false
      },
      {
        auctionID: "a2",
        url: "url2",
        matched: false
      }
    ]
  }
})

db.batch.insert({
  batchID: "b2",
  batchSpecification: {
    batchSpecificationID: "bs1",
    auctions: [
      {
        auctionID: "a3",
        url: "url3",
        matched: false
      },
      {
        auctionID: "a4",
        url: "url4",
        matched: false
      }
    ]
  }
})

db.batch.update({ batchID: "b1", "batchSpecification.auctions.auctionID": "a2" }, {
    $set: {
        "batchSpecification.auctions.$.matched": true
    }
}, { multi: true });

#
# ##################################################################################################
#
# POSTGRES
#
# ##################################################################################################
#
docker pull postgres

docker ps
docker exec -it number6_postgres-db_1 bash
psql -U number6 -W

#
# ##################################################################################################
#
# ELASTICSEARCH
#
# ##################################################################################################
#
# To start an ElasticSearch Docker instance for dev :
#
docker pull docker.elastic.co/elasticsearch/elasticsearch:7.10.2
docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:7.10.2
#
# Read: https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html
#
