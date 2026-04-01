import { Capacitor } from "@capacitor/core";

const host = Capacitor.getPlatform() === "android" ? "10.0.2.2" : "127.0.0.1";

export const url = `http://${host}:8000/index.php`;
