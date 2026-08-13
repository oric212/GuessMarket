package guessmarket.dto;
import java.util.List;

public record EventDTO (
    int id,
    String eventName,
    String description,
    int commissionPercentage,
    String commissionMethod,
    List <String> options
){
}

