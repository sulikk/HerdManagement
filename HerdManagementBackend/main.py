from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.assistant.router import router as assistant_router
from app.config import settings
from app.database import init_db
from app.irzplus.router import router as irzplus_router
from app.listings.router import router as listings_router
from app.routers import animals, events, health, sync, treatments


@asynccontextmanager
async def lifespan(application: FastAPI):
    init_db()
    yield


app = FastAPI(
    title="Herd Management API",
    version="0.1.0",
    description="Backend dla aplikacji mobilnej do zarządzania stadem bydła",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(animals.router)
app.include_router(events.router)
app.include_router(treatments.router)
app.include_router(sync.router)
app.include_router(listings_router)
app.include_router(irzplus_router)
app.include_router(assistant_router)
