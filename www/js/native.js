// Brücke zur Android-App (Capacitor). Im normalen Browser sind alle Exporte null.
const Cap = window.Capacitor;
export const isNative = !!Cap?.isNativePlatform?.();
const reg = name => (isNative && typeof Cap.registerPlugin === 'function' ? Cap.registerPlugin(name) : null);
export const AppPlugin = reg('App');
export const Filesystem = reg('Filesystem');
export const Share = reg('Share');
