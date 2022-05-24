{
    var x = check %s;

    if x is false {
        // handle stopping mediation midway
        return;
    } else if x is http:Response {
        http:ListenerError? response = caller->respond(x);
        return;
    }
}
