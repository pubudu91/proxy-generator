{
    var v = %s;

    if v is false|error {
        http:ListenerError? response = caller->respond(errFlowResponse);
        return;
    } else if v is http:Response {
        http:ListenerError? response = caller->respond(v);
        return;
    }
}
