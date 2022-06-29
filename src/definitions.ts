export interface InAppBrowserPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
