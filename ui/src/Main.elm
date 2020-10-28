module Main exposing (main)

import Ansi.Log
import Browser
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Html.Keyed as Keyed
import Html.Lazy
import Http
import Json.Decode exposing (Decoder, field, int, map2, map3, string)
import Task
import Time exposing (..)


type alias LogRecord =
    { id : String, message : String }


type alias Collection =
    { id : Int, name : String, size : Int }


type alias Model =
    { baseUrl: String
    , records : List LogRecord
    , loading : Bool
    , intervalSecs : Int
    , collections : List String
    , collection : Maybe String
    }


type Msg
    = FetchCollections
    | CollectionsReceived (Result Http.Error (List Collection))
    | FetchLastRecords
    | LastDataReceived (Result Http.Error (List LogRecord))
    | FetchRecords
    | DataReceived (Result Http.Error (List LogRecord))
    | ChangedInterval String
    | ChangedCollection String
    | ToggleLoading


init : String -> ( Model, Cmd Msg )
init baseUrl =
    ( { baseUrl = baseUrl, records = [], loading = True, intervalSecs = 3, collections = [], collection = Maybe.Nothing }
    , Task.perform identity (Task.succeed FetchCollections)
    )

update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        FetchCollections ->
            ( model, fetchCollections model)

        CollectionsReceived (Ok items) ->
            let
                names =
                    List.map .name items

                newModel =
                    { model | collections = names, collection = List.head names }
            in
            ( newModel, fetchLastLogs newModel )

        CollectionsReceived (Err error) ->
            ( model, Cmd.none )

        ChangedCollection collection ->
            let
                newModel =
                    { model | records = [], collection = Just collection }
            in
            ( newModel, fetchLastLogs newModel )

        FetchLastRecords ->
            ( model, fetchLastLogs model )

        LastDataReceived (Ok rows) ->
            ( { model | records = model.records ++ rows }, Cmd.none )

        LastDataReceived (Err error) ->
            ( model, Cmd.none )

        FetchRecords ->
            let
                last =
                    List.map (\x -> x.id) model.records
                        |> List.reverse
                        |> List.head
                        |> Maybe.withDefault "0"
            in
            ( model, fetchLogs model last )

        DataReceived (Ok rows) ->
            if List.length rows > 0 then
                ( { model | records = model.records ++ rows }, Task.perform identity (Task.succeed FetchRecords))

            else
                ( { model | records = model.records ++ rows }, Cmd.none )

        DataReceived (Err error) ->
            case error of
                _ ->
                    ( model, Cmd.none )

        ToggleLoading ->
            if model.loading == True then
                ( { model | loading = False }, Cmd.none )

            else
                ( { model | loading = True }, Cmd.none )

        ChangedInterval interval ->
            let
                _ =
                    Debug.log "interval " interval
            in
            ( { model | intervalSecs = Maybe.withDefault 3 <| String.toInt interval }, Cmd.none )


view : Model -> Html Msg
view model =
    let
        lastN : Int -> List a -> List a
        lastN n xs = List.drop (List.length xs - n) xs
        records =
            lastN 500 model.records
    in
    div []
        [ nav [ class "navbar fixed-top navbar-light bg-light" ]
            [ viewButton model
            , viewInterval model
            , viewCollections model
            ]
        , div [ class "container-fluid" ]
            [ Keyed.node "div"
                [ class "logs-wrapper" ]
                (List.map viewLogRecord records)
            ]
        ]


subscriptions : Model -> Sub Msg
subscriptions model =
    if model.loading == True then
        Sub.batch [ Time.every (toFloat model.intervalSecs * 1000) (\_ -> FetchRecords) ]

    else
        Sub.none


main : Program String Model Msg
main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }

-- Helper functions
logRecordDecoder : Decoder LogRecord
logRecordDecoder =
    map2 LogRecord
        (field "id" string)
        (field "message" string)


collectionDecoder : Decoder Collection
collectionDecoder =
    map3 Collection
        (field "id" int)
        (field "name" string)
        (field "size" int)


fetchCollections : Model -> Cmd Msg
fetchCollections model =
    Http.get
        { url = model.baseUrl ++ "/v1/collections"
        , expect = Http.expectJson CollectionsReceived (Json.Decode.list collectionDecoder)
        }


fetchLastLogs : Model -> Cmd Msg
fetchLastLogs model =
    case model.collection of
        Just collection ->
            Http.get
                { url = model.baseUrl ++ "/v1/logs/tail/" ++ collection ++ "?limit=10"
                , expect = Http.expectJson LastDataReceived (Json.Decode.list logRecordDecoder)
                }

        Nothing ->
            Cmd.none


fetchLogs : Model -> String -> Cmd Msg
fetchLogs model id =
    case model.collection of
        Just collection ->
            Http.get
                { url = model.baseUrl ++ "/v1/logs/" ++ collection ++ "?limit=100&after=" ++ id
                , expect = Http.expectJson DataReceived (Json.Decode.list logRecordDecoder)
                }

        Nothing ->
            Cmd.none


renderLog : String -> Html msg
renderLog message =
    let
        m =
            Ansi.Log.init Ansi.Log.Cooked

        u =
            Ansi.Log.update message m
    in
    Ansi.Log.view u


viewLogRecord : LogRecord -> ( String, Html Msg )
viewLogRecord record =
    ( record.id
    , div []
        [ Html.Lazy.lazy renderLog record.message
        ]
    )


viewButton : Model -> Html Msg
viewButton _ =
    button [ onClick ToggleLoading ] [ text "Pause/Play" ]


intervalOption : Model -> Int -> Html Msg
intervalOption model number =
    let
        slctd =
            if model.intervalSecs == number then
                True

            else
                False

        str =
            String.fromInt number
    in
    option [ value str, selected slctd ] [ text str ]


viewInterval : Model -> Html Msg
viewInterval model =
    select [ onInput ChangedInterval ]
        (List.map (intervalOption model) [ 1, 3, 5, 10, 30 ])


collectionOption : Model -> String -> Html Msg
collectionOption model name =
    let
        slctd =
            if model.collection == Just name then
                True

            else
                False
    in
    option [ value name, selected slctd ] [ text name ]


viewCollections : Model -> Html Msg
viewCollections model =
    select [ onInput ChangedCollection ]
        (List.map (collectionOption model) model.collections)

