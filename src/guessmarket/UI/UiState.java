package guessmarket.UI;


public enum UiState {
    StartMainMenu {
      @Override
        public UiState HandleScreen(String input) {
          return LoadedMainMenu;

      }
    },
    LoadedMainMenu {
        @Override
        public UiState HandleScreen(String input) {
            return switch (input) {
                case "1" -> LoadedMainMenu;
                case "2" -> MarketActions;
                default -> this;
            };
        }
    },
    MarketActions {
        @Override
        public UiState HandleScreen(String input) {
            return switch (input) {
                case "1" -> LoadedMainMenu;
                case "2" -> EnterEventId;
                default -> this;
            };
        }
    },
    ShowEventState {
        @Override
        public UiState HandleScreen(String input) {
        return MarketActions;
        }
    },
    EnterEventId {
        @Override
        public UiState HandleScreen(String input) {
            return ShowEventState;
        }
    },
    ParticipateInEvent {
        @Override
        public UiState HandleScreen(String input) {
               return switch (input) {
                   case "1" -> MarketActions;
                   case "4" -> PreTransactionScreen;
                   // case 2,3 act same as default, we are staying on the same screen
                   default -> ParticipateInEvent;
               };
        }
    },
    PreTransactionScreen {
        @Override
        public UiState HandleScreen(String input) {
           return  switch (input) {
                case "1" ->  PostTransactionScreen;
                case "2" ->  MarketActions;
                default ->  PreTransactionScreen;
            };
        }
    },
    PostTransactionScreen {
        @Override
        public UiState HandleScreen(String input) {
            return switch (input) {
                case "1" -> MarketActions;
                default -> PostTransactionScreen;
            };
        }
    },
    CloseEvent {
        @Override
        public UiState HandleScreen(String input) {
             return ShowEventState;
        }
    },
    ErrorScreen {
        @Override
        public UiState HandleScreen(String input) {
            if (input.equals("1"))
        }
    },

    EXIT {
        @Override
        public UiState HandleScreen(String input) {
              return EXIT;
        }
    };
    public UiState run (String input) {
       UiState nextState = this.HandleScreen(input);
        //this.display();
        return nextState;
    }

    public abstract UiState HandleScreen(String input);
}


