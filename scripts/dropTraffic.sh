#!/bin/bash


function usage(){
  echo "Usage: "
  echo " ./dropTraffic drop 0.2 - drop packets with probability 0.2"
  echo " ./dropTraffic clear - clear drop settings"
}

if [[ "$#" -eq 2 && "$1" == "drop" ]]
  then
   echo "Will drop $2 packets"
   exit 0
fi

if [[ "$#" -eq 1 && "$1" == "clear" ]]
 then
 echo "Clear drop config" 
 exit 0
fi  

usage



