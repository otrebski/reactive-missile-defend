#!/bin/bash


function usage(){
  echo "Usage: "
  echo " ./dropTraffic drop 0.2 - drop packets with probability 0.2"
  echo " ./dropTraffic clear - clear drop settings"
}

if [[ "$#" -eq 2 && "$1" == "drop" ]]
  then
   echo "Will drop $2 input and output packets"
   # for randomly dropping 10% of incoming packets:
   iptables -A INPUT -m statistic --mode random --probability $2 -j DROP
   # and for dropping 10% of outgoing packets:
   iptables -A OUTPUT -m statistic --mode random --probability $2 -j DROP
   exit 0
fi

if [[ "$#" -eq 1 && "$1" == "clear" ]]
 then
 echo "Clear drop config"
 iptables -F
 exit 0
fi  

usage



