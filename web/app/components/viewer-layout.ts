/**
 * Small-screen height for the result viewers so pack/shell/injector panels line up.
 * Desktop result layouts use fillHeight to subtract the actual connection-info height.
 */
export const RESULT_VIEWER_HEIGHT = "clamp(256px, calc(100dvh - 33rem - 2px), 720px)";
