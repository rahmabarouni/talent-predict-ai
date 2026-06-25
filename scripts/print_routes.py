import traceback
try:
    import main
    app = getattr(main, "app")
    print("ROUTES_START")
    for route in app.routes:
        methods = getattr(route, "methods", None)
        if methods is None:
            print(str(getattr(route, "path", "<no-path>")) + " [NO_METHODS]")
        else:
            print(str(route.path) + " " + str(sorted(methods)))
    print("ROUTES_END")
except Exception:
    traceback.print_exc()
