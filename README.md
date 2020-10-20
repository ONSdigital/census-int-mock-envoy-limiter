# census-int-mock-envoy-limiter

A mock implementation for test use of the envoy proxy rate limiter

## Limits

See CR-1204 for the definition of the limits used by the real rate limiter.
This CR contains an excellent diagram which shows the data and limits to be used by the genuine Envoy Limiter. 

## Endpoints

To manually test the endpoints run 'scripts/invokeMockEnvoyLimiter.sh' 

** Control success or failure response - /limit **

To tell the mock to respond with 429 (too many requests) set 'enabled' to true: 

    curl -X POST -H "Content-Type: application/json" "http://localhost:8181/limit?enabled=true"

And to return to the default, which responds with an http 200, set 'enabled' to false:

    curl -X POST -H "Content-Type: application/json" "http://localhost:8181/limit?enabled=false"

**     Submit data for a limiter check - /json **

Before calling the json endpoint first create some test data:

```
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
```

Now send the above data to the mock limiter:

    curl -s --data @/tmp/test1.json -H 'Content-Type: application/json' http://localhost:8181/json | jq

** Retrieve mock activity - /requests **

Ask the mock limiter to return a list of requests made since the last call to '/limit'. 
If the limit endpoint has not been used then all requests since startup are returned.

    curl -s -H 'Content-Type: application/json' http://localhost:8181/requests | jq
