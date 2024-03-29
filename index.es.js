import { NativeModules, NativeEventEmitter } from 'react-native';

/*! *****************************************************************************
Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
***************************************************************************** */

function __awaiter(thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
}

function __generator(thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (_) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
}

var BLECore = NativeModules.BLECore;
var BLECoreEmitter = new NativeEventEmitter(BLECore);
var peripheralStates = ["disconnected", "connecting", "connected", "disconnecting"];
var init = function (roles, options) { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._initialize(roles, options)];
            case 1:
                _a.sent();
                return [2 /*return*/];
        }
    });
}); };
var startScanning = function (serviceUUIDs, options) { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._startScanning(serviceUUIDs || [], options)];
            case 1:
                _a.sent();
                return [2 /*return*/];
        }
    });
}); };
var stopScanning = function () { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._stopScanning()];
            case 1:
                _a.sent();
                return [2 /*return*/];
        }
    });
}); };
var onPeripheralDiscovered = function (handlePeripheralDiscovered) {
    return BLECoreEmitter.addListener("peripheralDiscovered", function (peripheral) { return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    peripheral.state = peripheralStates[peripheral.state];
                    return [4 /*yield*/, handlePeripheralDiscovered(peripheral)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    }); });
};
var onPeripheralDisconnected = function (handlePeripheralDisconnected) {
    return BLECoreEmitter.addListener("peripheralDisconnected", function (peripheral) { return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    peripheral.state = peripheralStates[peripheral.state];
                    return [4 /*yield*/, handlePeripheralDisconnected(peripheral)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    }); });
};
var connectToPeripheral = function (_a, options) {
    var id = _a.id;
    return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0: return [4 /*yield*/, BLECore._connectToPeripheral(id, options)];
                case 1: return [2 /*return*/, _b.sent()];
            }
        });
    });
};
var discoverPeripheralServices = function (_a, serviceUUIDs) {
    var id = _a.id;
    return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0: return [4 /*yield*/, BLECore._discoverPeripheralServices(id, serviceUUIDs)];
                case 1: return [2 /*return*/, _b.sent()];
            }
        });
    });
};
var discoverPeripheralCharacteristics = function (_a, serviceUUID, characteristicsUUIDs) {
    var id = _a.id;
    return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0: return [4 /*yield*/, BLECore._discoverPeripheralCharacteristics(id, serviceUUID, characteristicsUUIDs)];
                case 1: return [2 /*return*/, _b.sent()];
            }
        });
    });
};
var readCharacteristicValueForPeripheral = function (_a, serviceUUID, characteristicUUID) {
    var id = _a.id;
    return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0: return [4 /*yield*/, BLECore._readCharacteristicValueForPeripheral(id, serviceUUID, characteristicUUID)];
                case 1: return [2 /*return*/, _b.sent()];
            }
        });
    });
};
var startAdvertising = function (services) { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._startAdvertising(services)];
            case 1:
                _a.sent();
                return [2 /*return*/];
        }
    });
}); };
var stopAdvertising = function () { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._stopAdvertising()];
            case 1:
                _a.sent();
                return [2 /*return*/];
        }
    });
}); };
var onCentralConnected = function (handleCentralConnected) {
    return BLECoreEmitter.addListener("centralConnected", function (central) { return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, handleCentralConnected(central)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    }); });
};
var onCentralDisconnected = function (handleCentralDisconnected) {
    return BLECoreEmitter.addListener("centralDisconnected", function (central) { return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, handleCentralDisconnected(central)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    }); });
};
var onReadRequestReceived = function (handleReadRequestReceived) {
    return BLECoreEmitter.addListener("receivedReadRequest", function (request) { return __awaiter(void 0, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, handleReadRequestReceived(request)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    }); });
};
var respondToReadRequest = function (requestId, accept) { return __awaiter(void 0, void 0, void 0, function () {
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0: return [4 /*yield*/, BLECore._respondToReadRequest(requestId, accept)];
            case 1: return [2 /*return*/, _a.sent()];
        }
    });
}); };
var index = {
    init: init,
    startScanning: startScanning,
    stopScanning: stopScanning,
    onPeripheralDiscovered: onPeripheralDiscovered,
    onPeripheralDisconnected: onPeripheralDisconnected,
    connectToPeripheral: connectToPeripheral,
    discoverPeripheralServices: discoverPeripheralServices,
    discoverPeripheralCharacteristics: discoverPeripheralCharacteristics,
    readCharacteristicValueForPeripheral: readCharacteristicValueForPeripheral,
    startAdvertising: startAdvertising,
    stopAdvertising: stopAdvertising,
    onCentralConnected: onCentralConnected,
    onCentralDisconnected: onCentralDisconnected,
    onReadRequestReceived: onReadRequestReceived,
    respondToReadRequest: respondToReadRequest
};
var GenericAccessProfileRole;
(function (GenericAccessProfileRole) {
    GenericAccessProfileRole[GenericAccessProfileRole["PERIPHERAL"] = 0] = "PERIPHERAL";
    GenericAccessProfileRole[GenericAccessProfileRole["CENTRAL"] = 1] = "CENTRAL";
})(GenericAccessProfileRole || (GenericAccessProfileRole = {}));

export default index;
export { GenericAccessProfileRole };
//# sourceMappingURL=index.es.js.map
