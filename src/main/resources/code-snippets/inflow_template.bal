do {
    var x = check %s;

    if x is false {
        http:Response res1 = createAcceptedResponse();
        http:ListenerError? response = caller->respond(res1);
        return;
    } else if x is http:Response {
        http:ListenerError? response = caller->respond(x);
        return;
    }
}
