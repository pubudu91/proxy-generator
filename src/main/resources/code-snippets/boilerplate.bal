
final http:Client backendEP = check new("http://localhost:9090");

function createDefaultErrorResponse(error e) returns http:Response {
    return new;
}

function createAcceptedResponse() returns http:Response {
    return new;
}

function copyRequestHeaders(http:Request req) returns map<string|string[]> {
    map<string|string[]> headers = {};
    string[] headerNames = req.getHeaderNames();
    foreach string name in headerNames {
        string[]|http:HeaderNotFoundError headersResult = req.getHeaders(name);

        if headersResult is string[] {
            if headersResult.length() == 1 {
              headers[name] = headersResult[0];
            } else {
              headers[name] = headersResult;
            }
        }
    }
    return headers;
}
