export declare function startReader(): Promise<boolean>;
export declare function stopReader(): Promise<null>;

export declare function StartScan(): () => void;
export declare function StopScan(): () => void;

export declare function on(val: 'barcodeReadSuccess' | 'barcodeReadFail', callback: () => void): void;
export declare function off(val: 'barcodeReadFail', callback: () => void): void;
