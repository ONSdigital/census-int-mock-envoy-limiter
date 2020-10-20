#!/bin/bash
set -e


cat > /tmp/test1.json <<EOF
{
  "domain": "respondenthome",
  "descriptors": [
    {
      "entries": [
        {"key": "productGroup", "value":  "UAC"},
        {"key": "individual", "value":  "false"},
        {"key": "deliveryChannel", "value":  "SMS"},
        {"key": "caseType", "value":  "HH"},
        {"key": "uprn", "value":  "987"}
      ]
    },
    {
      "entries": [
        {"key": "productGroup", "value":  "UAC"},
        {"key": "individual", "value":  "false"},
        {"key": "deliveryChannel", "value":  "SMS"},
        {"key": "caseType", "value":  "HH"},
        {"key": "telNo", "value":  "07968583119"}
      ]
    },
    {
      "entries": [
        {"key": "productGroup", "value":  "UAC"},
        {"key": "individual", "value":  "false"},
        {"key": "deliveryChannel", "value":  "SMS"},
        {"key": "caseType", "value":  "HH"},
        {"key": "ipAddress", "value":  "123.123.123.123"}
      ]
    }
  ]
}
EOF


echo "1) Hit info endpoint:"
curl -s -H "Content-Type: application/json" "http://localhost:8181/info" | jq
echo -e "\n"

echo "2) Turn on limits. Expecting http 429 status now:" 
curl -s -X POST -H "Content-Type: application/json" "http://localhost:8181/limit?enabled=true"
echo -e "\n"

echo "3) Send in a couple of requests:"
curl -s --data @/tmp/test1.json -H 'Content-Type: application/json' http://localhost:8181/json | jq
curl -s --data @/tmp/test1.json -H 'Content-Type: application/json' http://localhost:8181/json | jq
echo -e "\n"

echo "4) Get captured requests:"
curl -s -H 'Content-Type: application/json' http://localhost:8181/requests | jq
echo -e "\n"


echo "5) Switch limiter off. Back to http 200 status:"
curl -s -X POST -H "Content-Type: application/json" "http://localhost:8181/limit?enabled=false"
echo -e "\n"

echo "6) Send in request:"
curl -s --data @/tmp/test1.json -H 'Content-Type: application/json' http://localhost:8181/json | jq
echo -e "\n"

echo "7) Get captured requests:"
curl -s -H 'Content-Type: application/json' http://localhost:8181/requests | jq
echo -e "\n"
