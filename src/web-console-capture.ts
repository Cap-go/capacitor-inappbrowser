export const CAPGO_CONSOLE_POST_TYPE = 'capgo-inappbrowser-console';

export type CapgoConsoleLevel = 'log' | 'info' | 'warn' | 'error' | 'debug';

export type CapgoConsolePayload = {
  level: CapgoConsoleLevel;
  message: string;
  source?: string;
  line?: number;
  column?: number;
};

export function normalizeConsoleLevel(level: unknown): CapgoConsoleLevel {
  const normalized = typeof level === 'string' ? level.trim().toLowerCase() : '';
  switch (normalized) {
    case 'log':
    case 'info':
    case 'warn':
    case 'error':
    case 'debug':
      return normalized;
    case 'tip':
      return 'info';
    case 'warning':
      return 'warn';
    case 'assert':
      return 'error';
    default:
      return 'log';
  }
}

export function parseCapgoConsolePostMessage(data: unknown): CapgoConsolePayload | null {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return null;
  }
  const record = data as Record<string, unknown>;
  if (record.type !== CAPGO_CONSOLE_POST_TYPE) {
    return null;
  }
  const message = record.message;
  if (typeof message !== 'string') {
    return null;
  }
  const payload: CapgoConsolePayload = {
    level: normalizeConsoleLevel(record.level),
    message,
  };
  if (typeof record.source === 'string' && record.source.length > 0) {
    payload.source = record.source;
  }
  const line = integerOrUndefined(record.line);
  if (line !== undefined) {
    payload.line = line;
  }
  const column = integerOrUndefined(record.column);
  if (column !== undefined) {
    payload.column = column;
  }
  return payload;
}

function integerOrUndefined(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return undefined;
}

/** Injected into a same-origin opened window; forwards console output to window.opener. */
export function buildConsoleCaptureScript(): string {
  const postType = CAPGO_CONSOLE_POST_TYPE;
  return `(function(){
if(window.__capgoInAppBrowserConsoleCaptureInstalled){return;}
window.__capgoInAppBrowserConsoleCaptureInstalled=true;
var postType=${JSON.stringify(postType)};
var opener=window.opener;
if(!opener||typeof opener.postMessage!=='function'){return;}
var serialize=function(value){
if(value===undefined){return 'undefined';}
if(value instanceof Error){return value.stack||value.message||String(value);}
if(typeof value==='string'){return value;}
try{
var json=JSON.stringify(value);
if(json!==undefined){return json;}
}catch(_e){}
try{return String(value);}catch(_e2){return '[unserializable]';}
};
var post=function(level,values,metadata){
try{
var parts=Array.prototype.slice.call(values||[]).map(serialize);
opener.postMessage({
type:postType,
level:level,
message:parts.join(' '),
source:metadata&&metadata.source?metadata.source:window.location.href,
line:metadata&&typeof metadata.line==='number'?metadata.line:undefined,
column:metadata&&typeof metadata.column==='number'?metadata.column:undefined
},window.location.origin);
}catch(_err){}
};
['log','info','warn','error','debug'].forEach(function(level){
var original=console[level]?console[level].bind(console):null;
console[level]=function(){
post(level,arguments,null);
if(original){return original.apply(console,arguments);}
};
});
var originalAssert=console.assert?console.assert.bind(console):null;
console.assert=function(condition){
if(!condition){
var args=Array.prototype.slice.call(arguments,1);
post('error',args.length?['Assertion failed:'].concat(args):['Assertion failed'],null);
}
if(originalAssert){return originalAssert.apply(console,arguments);}
};
})();`;
}
