# proxy-generator

Currently, given a Ballerina source code like the following,
```ballerina
import ballerina/http;

listener http:Listener ep0 = new (8243, config = {host: "localhost"});

service /pizzashack/'1\.0\.0 on ep0 {
    resource function post 'order(@http:Payload json p) returns record {|*http:Created; Order body;|}|record {|*http:BadRequest; Error body;|}|record {|*http:RangeNotSatisfied; Error body;|} {
    }
}
```

This will generate a modified version which results in the following:
```ballerina
import ballerina/http;

listener http:Listener ep0 = new (8243, config = {host: "localhost"});

service /pizzashack/'1\.0\.0 on ep0 {
    resource function post 'order(http:Caller caller, http:Request incomingReq, @http:Payload json p) returns error? {
        do {
            {
                var x = check fooInMediate(incomingReq);

                if x is false {
                    http:Response res1 = createAcceptedResponse();
                    http:ListenerError? response = caller->respond(res1);
                    return;
                } else if x is http:Response {
                    http:ListenerError? response = caller->respond(x);
                    return;
                }
            }

            http:Response res = check backendEP->post("...", incomingReq);

            {
                var x = check fooOutMediate(res, incomingReq);

                if x is false {
                    // handle stopping mediation midway
                    return;
                } else if x is http:Response {
                    http:ListenerError? response = caller->respond(x);
                    return;
                }
            }

            check caller->respond(res);
        } on fail var e {
            http:Response errorRes = createDefaultErrorResponse();
            // call_error_flow{ };
            check caller->respond(errorRes);
        }
    }
}

http:Client backendEP = check new ("http://localhost:9090");

function createDefaultErrorResponse() returns http:Response {
    return new;
}

function createAcceptedResponse() returns http:Response {
    return new;
}
```