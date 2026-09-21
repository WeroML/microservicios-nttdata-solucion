package tacos.api.dto;

public class ReorderRequest {
    private String paymentMethodId;
    private boolean confirmPriceChange;
    
    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    
    public boolean isConfirmPriceChange() { return confirmPriceChange; }
    public void setConfirmPriceChange(boolean confirmPriceChange) { this.confirmPriceChange = confirmPriceChange; }
}
