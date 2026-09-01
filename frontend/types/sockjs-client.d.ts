/**
 * sockjs-client 未自带类型声明，这里补最小声明。
 * 与 @stomp/stompjs 搭配使用：new Client({ webSocketFactory: () => new SockJS('/ws') })
 */
declare module "sockjs-client" {
  class SockJS {
    constructor(url: string, protocols?: string | string[], options?: Record<string, unknown>);
    close(code?: number, reason?: string): void;
    send(data: string): void;
    readyState: number;
    onopen?: () => void;
    onclose?: (e?: unknown) => void;
    onmessage?: (e?: { data?: unknown }) => void;
  }
  export default SockJS;
}
