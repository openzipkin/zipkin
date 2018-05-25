package zipkin.filter;

public class FilterActivatedException extends Throwable {
  private final int httpCode;

  public FilterActivatedException(String message, int httpCode) {
    super(message);
    this.httpCode = httpCode;
  }

  public int getHttpCode() {
    return httpCode;
  }
}
