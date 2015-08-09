# Reactive Missile Defend
# How cluster look like #
Cluster should have following nodes:

* Shared journal node (Shared level DB) (sbt sj)
* Swing UI with Game engine (sbt ui)
* Command centers to run defence tower logic (choosing target and calculating interception vector) (sbt cc)
* Optional CLI UI (sbt cliui)

# How to run only local nodes: #
First start shared journal. It will be also seed node. This node is Single Point Of Failure, so do not kill it. First export configuration:

* export SEED_NODE=akka.tcp://defend@127.0.0.1:3000
* export HOST=127.0.0.1
* export PORT=3000
* sbt sj

Run Swing UI. This node is also game engine. Do not kill it. In another terminal run

* export SEED_NODE=akka.tcp://defend@127.0.0.1:3000
* export HOST=127.0.0.1
* export PORT=2500
* sbt ui

Run Command Centers nodes (as many as you want). Every instance need to have different PORT

* export SEED_NODE=akka.tcp://defend@127.0.0.1:3000
* export HOST=127.0.0.1
* export PORT=2551
* sbt cc

In UI choose click "Start" to begin invasion. When attack is running you can kill or restart Command centers node and watch what will happened.

Enjoy