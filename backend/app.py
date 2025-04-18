import os
import socket

from flask import Flask, request, jsonify
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from langchain_groq import ChatGroq
from langchain.prompts import ChatPromptTemplate
from langchain_community.document_loaders import YoutubeLoader
from flask_cors import CORS
from httplib2 import Http
import logging

app = Flask(__name__)
CORS(app)
logging.basicConfig(level=logging.DEBUG)

http = Http(timeout=20)
youtube = build("youtube", "v3", developerKey=os.getenv('YT_API_KEY'), http=http)

@app.route("/get-summary", methods=["POST"])
def get_summary():
    data = request.get_json()
    app.logger.debug(f'[SUMMARY_DEBUG] data: {data}')
    thumb = data.get('thumb', '')
    title = data.get("title", "")
    channel = data.get("channel", "")
    debug = str(data.get("debug", True)) != "False"

    # If you don't configure your groq and yt keys, just use a placeholder as response
    if debug:
        return jsonify({'summary': "Não deu ruim. Ele pulou do prédio mas abriu o paraquedas e pousou em segurança."})

    try:
        results = youtube.search().list(
            q=f"{title} {channel}",
            part="snippet",
            maxResults=5
        ).execute()
    except socket.timeout:
        app.logger.error(
            f"[SUMMARY_ERROR] Timeout while requesting YouTube API for title: '{title}', channel: '{channel}'")
        return {
            "error": "A requisição à API do YouTube demorou demais e foi interrompida. Tente novamente em instantes."}, 504
    except HttpError as e:
        app.logger.error(f"[SUMMARY_ERROR] YouTube API error: {e}")
        return {"error": "Erro ao acessar a API do YouTube. Verifique a chave ou tente novamente mais tarde."}, 502
    except Exception as e:
        app.logger.exception(f"[SUMMARY_ERROR] Unexpected error: {e}")
        return {"error": "Erro inesperado ao processar sua solicitação."}, 500


    url = ''
    for item in results["items"]:
        vid = item["id"].get("videoId")
        if vid:
            url = f"https://www.youtube.com/watch?v={vid}"
            break
    app.logger.debug(f'[SUMMARY_DEBUG] url: {url}')

    try:
        # It is not allowed to scrape yt video info for commercial usage, so the following will
        # most likely be blocked if run on cloud providers, but works fine on local machines
        loader = YoutubeLoader.from_youtube_url(url, language=['pt'])
        document_list = loader.load()
    except Exception as e:
        app.logger.exception(f'[SUMMARY_DEBUG] e: {e}')
        return jsonify({"error": "Failed to load YouTube video"}), 400

    if not document_list:
        app.logger.debug(f'[SUMMARY_DEBUG] document_list: {document_list}')
        return jsonify({"error": "No documents found"}), 404

    system_message = {
        'role': 'system',
        'content': (
            "Você é Fiorelo, um assistente que resume vídeos do YouTube para ajudar os usuários a ganhar tempo. "
            "Ignore anúncios, saudações, despedidas, agradecimentos e qualquer coisa fora do assunto principal. "
            "Após o resumo, responda perguntas extras com base no vídeo e no seu conhecimento geral. "
            "Por se tratar de uma transcrição, poderam ter muitos erros de escrita. "
            "Se atente a incoerências que podem ser erro da transcrição automática do vídeo. "
            "Evite frases como 'A transcriçao do vídeo fala sobre..'."
        )
    }
    transcript = {'role': 'system', 'content': f"Transcrição do vídeo: {document_list[0].page_content[:20000]}"}
    template = ChatPromptTemplate.from_messages([
        system_message,
        transcript,
        ('system', "Faça um resumo da transcrição acima.")
    ])

    chat = ChatGroq(model='llama-3.3-70b-versatile', api_key=os.getenv("GROQ_API_KEY"))
    chain = template | chat
    response = chain.invoke({})
    print(response.content)
    return jsonify({'summary': response.content})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
