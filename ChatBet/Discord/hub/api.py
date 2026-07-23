"""
FastAPI hub for ChatBet Discord + RuneLite interop.

RuneLite (and tools) call these endpoints with X-Api-Key when configured.
The Discord bot uses in-process BetEngine and does not need the key.
"""

from __future__ import annotations

from typing import Any, Dict, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from config import Settings, get_settings
from hub.engine import BetEngine
from hub.models import (
    ApiResponse,
    BalanceCredit,
    BalanceSet,
    BetPlace,
    PollCreate,
    PollResolve,
)


def create_app(engine: BetEngine, settings: Optional[Settings] = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="ChatBet Hub", version="1.0.0")

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    def require_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
        expected = (settings.hub_api_key or "").strip()
        if not expected:
            return  # open local mode
        if not x_api_key or x_api_key.strip() != expected:
            raise HTTPException(status_code=401, detail="Invalid or missing X-Api-Key")

    @app.get("/health")
    def health() -> Dict[str, Any]:
        return {"ok": True, "service": "chatbet-hub"}

    @app.get("/api/v1/state")
    def get_state(_: None = Depends(require_api_key)) -> Dict[str, Any]:
        return engine.snapshot()

    @app.get("/api/v1/polls")
    def list_polls(
        include_closed: bool = Query(True),
        _: None = Depends(require_api_key),
    ) -> Dict[str, Any]:
        active = [engine.poll_view(p).model_dump() for p in engine.list_active()]
        closed = [engine.poll_view(p).model_dump() for p in engine.list_closed()] if include_closed else []
        return {"active": active, "closed": closed}

    @app.get("/api/v1/polls/{poll_id}")
    def get_poll(poll_id: int, _: None = Depends(require_api_key)) -> Dict[str, Any]:
        poll = engine.get_poll(poll_id)
        if poll is None:
            raise HTTPException(status_code=404, detail="Poll not found")
        return engine.poll_view(poll).model_dump()

    @app.post("/api/v1/polls", response_model=ApiResponse)
    def create_poll(body: PollCreate, _: None = Depends(require_api_key)) -> ApiResponse:
        try:
            poll = engine.create_poll(
                question=body.question,
                options=body.options,
                bet_type=body.bet_type,
                resolution_trigger=body.resolution_trigger,
                poll_id=body.id,
                source=body.source,
            )
        except ValueError as e:
            raise HTTPException(status_code=400, detail=str(e)) from e
        return ApiResponse(ok=True, message="Poll created", data=engine.poll_view(poll).model_dump())

    @app.post("/api/v1/polls/{poll_id}/resolve", response_model=ApiResponse)
    def resolve_poll(poll_id: int, body: PollResolve, _: None = Depends(require_api_key)) -> ApiResponse:
        try:
            winning_idx = body.winning_option_index
            if body.winning_option:
                poll = engine.get_poll(poll_id)
                if poll is None:
                    raise ValueError("Poll not found or already closed")
                winning_idx = engine.resolve_option_index(poll, body.winning_option)
            poll = engine.resolve_poll(poll_id, winning_idx)
        except ValueError as e:
            raise HTTPException(status_code=400, detail=str(e)) from e
        return ApiResponse(ok=True, message="Poll resolved", data=engine.poll_view(poll).model_dump())

    @app.post("/api/v1/polls/resolve-trigger", response_model=ApiResponse)
    def resolve_trigger(
        trigger: str = Query(...),
        winning_option_index: int = Query(0),
        _: None = Depends(require_api_key),
    ) -> ApiResponse:
        resolved = engine.resolve_by_trigger(trigger, winning_option_index)
        return ApiResponse(
            ok=True,
            message=f"Resolved {len(resolved)} poll(s)",
            data=[engine.poll_view(p).model_dump() for p in resolved],
        )

    @app.post("/api/v1/bets", response_model=ApiResponse)
    def place_bet(body: BetPlace, _: None = Depends(require_api_key)) -> ApiResponse:
        try:
            wager = engine.place_wager(
                username=body.username,
                poll_id=body.poll_id,
                option=body.option,
                amount=body.amount,
                source=body.source,
                discord_user_id=body.discord_user_id,
            )
            poll = engine.get_poll(body.poll_id)
            return ApiResponse(
                ok=True,
                message="Bet placed",
                data={
                    "wager": {
                        "username": wager.username,
                        "poll_id": wager.poll_id,
                        "option_index": wager.option_index,
                        "amount": wager.amount,
                        "source": wager.source,
                    },
                    "balance": engine.get_balance(body.username),
                    "poll": engine.poll_view(poll).model_dump() if poll else None,
                },
            )
        except ValueError as e:
            raise HTTPException(status_code=400, detail=str(e)) from e

    @app.get("/api/v1/balance/{username}")
    def get_balance(username: str, _: None = Depends(require_api_key)) -> Dict[str, Any]:
        return {"username": username, "balance": engine.get_balance(username)}

    @app.post("/api/v1/balance", response_model=ApiResponse)
    def set_balance(body: BalanceSet, _: None = Depends(require_api_key)) -> ApiResponse:
        bal = engine.set_balance(body.username, body.amount)
        return ApiResponse(ok=True, message="Balance updated", data={"username": body.username, "balance": bal})

    @app.post("/api/v1/balance/credit", response_model=ApiResponse)
    def credit_balance(body: BalanceCredit, _: None = Depends(require_api_key)) -> ApiResponse:
        bal = engine.credit_balance(body.username, body.amount)
        return ApiResponse(
            ok=True,
            message=f"Credited {body.amount} ({body.reason})",
            data={"username": body.username, "balance": bal, "credited": body.amount},
        )

    @app.get("/api/v1/leaderboard")
    def leaderboard(limit: int = Query(10, ge=1, le=50), _: None = Depends(require_api_key)) -> Dict[str, Any]:
        rows = [{"username": u, "balance": b} for u, b in engine.leaderboard(limit)]
        return {"leaderboard": rows}

    @app.post("/api/v1/polls/{poll_id}/discord-message")
    def set_discord_message(
        poll_id: int,
        message_id: str = Query(...),
        _: None = Depends(require_api_key),
    ) -> ApiResponse:
        engine.set_discord_message_id(poll_id, message_id)
        return ApiResponse(ok=True, message="Message id stored")

    # Expose engine for the bot process
    app.state.engine = engine
    app.state.settings = settings
    return app
