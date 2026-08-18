package guessmarket.engine.domain;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity);

    EventStateDTO closeEvent(int eventId, int winningOptionIndex);

    void saveState(String filePath) throws IOException;

    static Engine loadState(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
            return (Engine) in.readObject();
        }
    }
}
