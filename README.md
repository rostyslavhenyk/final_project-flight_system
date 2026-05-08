
# Glide Airways - The Reliable Flight System

## Generative AI acknowledgement

The overall page layout, project direction, and final testing/review were handled by the team, but generative AI was used to help us understand some parts of the code, generate spoof data, and help with some front-end design choices.

AI was used to explain how to debug merging/integrating the flight part of the code, and to check suggestions on how the data structure could be condensed into previously existing tables. The suggestions were followed, and the code has been reviewed since and proven to work.

The main AI-assisted areas were:

- `src/main/kotlin/data/flight/FlightConnectionBuilder.kt` - support with understanding and adapting the connecting-flight logic.
- `src/main/kotlin/data/flight/FlightScheduleGenerator.kt` and `src/main/kotlin/data/flight/FlightSeedData.kt` - spoof data for testing and demonstration.
- `src/main/kotlin/routes/flight/FlightBookingHelpers.kt` - initial query about how one would carry the data throughout pages, which was built on later.
- `src/main/resources/static/js/homepage.js` and `src/main/resources/static/js/flights-results.js` - used AI to explain the server-to-front-end data display, and some UI choices.
- `src/main/resources/static/js/homepage.js`, `src/main/resources/templates/user/homepage/index.peb`, and `src/main/resources/static/css/homepage.css` - used AI to solve front-end bugs and alignment issues that I do not know how to, helped me implement Flatpickr to display dates, and make each option to have a white background colour instead of the default black one. Also used AI to auto generate comments for each chunk to facilitate merging with other branches (only a few was kept in the end).
- `src/main/kotlin/routes/flight/SearchFlightsPageModels.kt` - inspired by AI to use queryParams to detect user input and use the data to generate choose flight pages
- `src/main/kotlin/routes/flight/BookSeatsPageModels.kt` - consulted AI to solve why offers are disappearing and added fallback preventions for JSON storage.
- Asked AI to scan for potential fallbacks of step 3 seats and extras, and it suggested when there are 9 passengers the names will overwhelm the URL, making data hard to find. It suggested a solution of combining all the names into 1 parameter instead of 9, and expressed it in base64url format to prevent data breaking of the names
- Asked AI to explain what kind of stuff should be covered by tests.
- Had to ask to highlight as to why the page was overloading `src/main/resources/templates/staff/chat/index.peb`, and then followed the advice for it to work fine.
Front-end behaviour and booking/search flows were manually tested in the running application.
