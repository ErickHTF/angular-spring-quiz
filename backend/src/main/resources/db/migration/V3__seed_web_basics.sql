INSERT INTO quizzes (id, title)
VALUES ('web-basics', 'Fundamentos da Web')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;

INSERT INTO questions (id, quiz_id, position, prompt, duration_seconds)
VALUES
  ('web-q1', 'web-basics', 1, 'Qual elemento HTML define o título principal visível no conteúdo da página?', 20),
  ('web-q2', 'web-basics', 2, 'Qual propriedade CSS altera a cor do texto de um elemento?', 20),
  ('web-q3', 'web-basics', 3, 'Qual método HTTP busca dados sem modificar recursos no servidor?', 20),
  ('web-q4', 'web-basics', 4, 'Qual é o papel principal do JavaScript em uma página web?', 20),
  ('web-q5', 'web-basics', 5, 'Qual seletor CSS seleciona um elemento pelo valor do seu atributo id?', 20),
  ('web-q6', 'web-basics', 6, 'O que significa a sigla API?', 20),
  ('web-q7', 'web-basics', 7, 'Qual atributo do elemento <a> define o destino de um link?', 20),
  ('web-q8', 'web-basics', 8, 'Qual código de status HTTP indica que uma página não foi encontrada?', 20),
  ('web-q9', 'web-basics', 9, 'Qual propriedade CSS controla o espaço interno entre o conteúdo e a borda de um elemento?', 20),
  ('web-q10', 'web-basics', 10, 'Qual operador JavaScript compara valor E tipo ao mesmo tempo?', 20),
  ('web-q11', 'web-basics', 11, 'Qual método JavaScript seleciona um elemento do DOM pelo seu id?', 20),
  ('web-q12', 'web-basics', 12, 'Qual tag HTML inclui um arquivo JavaScript externo na página?', 20),
  ('web-q13', 'web-basics', 13, 'Para que serve principalmente o display: flex no CSS?', 20),
  ('web-q14', 'web-basics', 14, 'O que diferencia o HTTPS do HTTP?', 20),
  ('web-q15', 'web-basics', 15, 'Qual declaração JavaScript cria uma variável que não pode ser reatribuída?', 20),
  ('web-q16', 'web-basics', 16, 'Um elemento tem id="x", class="c" e style="color: red". No CSS: #x { color: blue !important } e .c { color: green }. Se o JS executar document.getElementById("x").style.color = "orange";, de que cor fica o texto?', 20)
ON CONFLICT (id) DO UPDATE SET
  position = EXCLUDED.position,
  prompt = EXCLUDED.prompt,
  duration_seconds = EXCLUDED.duration_seconds;

INSERT INTO choices (id, question_id, position, label, is_correct)
VALUES
  ('web-q1-a', 'web-q1', 1, '<h1>', true),
  ('web-q1-b', 'web-q1', 2, '<main>', false),
  ('web-q1-c', 'web-q1', 3, '<header>', false),
  ('web-q1-d', 'web-q1', 4, '<title>', false),

  ('web-q2-a', 'web-q2', 1, 'background-color', false),
  ('web-q2-b', 'web-q2', 2, 'font-style', false),
  ('web-q2-c', 'web-q2', 3, 'color', true),
  ('web-q2-d', 'web-q2', 4, 'text-decoration', false),

  ('web-q3-a', 'web-q3', 1, 'POST', false),
  ('web-q3-b', 'web-q3', 2, 'GET', true),
  ('web-q3-c', 'web-q3', 3, 'PATCH', false),
  ('web-q3-d', 'web-q3', 4, 'DELETE', false),

  ('web-q4-a', 'web-q4', 1, 'Estruturar o conteúdo da página', false),
  ('web-q4-b', 'web-q4', 2, 'Definir estilos visuais', false),
  ('web-q4-c', 'web-q4', 3, 'Adicionar interatividade e comportamento', true),
  ('web-q4-d', 'web-q4', 4, 'Gravar dados diretamente no banco', false),

  ('web-q5-a', 'web-q5', 1, '.menu', false),
  ('web-q5-b', 'web-q5', 2, '#menu', true),
  ('web-q5-c', 'web-q5', 3, 'menu', false),
  ('web-q5-d', 'web-q5', 4, '@menu', false),

  ('web-q6-a', 'web-q6', 1, 'Application Programming Interface', true),
  ('web-q6-b', 'web-q6', 2, 'Application Page Index', false),
  ('web-q6-c', 'web-q6', 3, 'Applied Program Internet', false),
  ('web-q6-d', 'web-q6', 4, 'Advanced Protocol Integration', false),

  ('web-q7-a', 'web-q7', 1, 'src', false),
  ('web-q7-b', 'web-q7', 2, 'href', true),
  ('web-q7-c', 'web-q7', 3, 'link', false),
  ('web-q7-d', 'web-q7', 4, 'action', false),

  ('web-q8-a', 'web-q8', 1, '200', false),
  ('web-q8-b', 'web-q8', 2, '301', false),
  ('web-q8-c', 'web-q8', 3, '404', true),
  ('web-q8-d', 'web-q8', 4, '500', false),

  ('web-q9-a', 'web-q9', 1, 'margin', false),
  ('web-q9-b', 'web-q9', 2, 'padding', true),
  ('web-q9-c', 'web-q9', 3, 'gap', false),
  ('web-q9-d', 'web-q9', 4, 'border-spacing', false),

  ('web-q10-a', 'web-q10', 1, '=', false),
  ('web-q10-b', 'web-q10', 2, '==', false),
  ('web-q10-c', 'web-q10', 3, '===', true),
  ('web-q10-d', 'web-q10', 4, '=>', false),

  ('web-q11-a', 'web-q11', 1, 'document.getElementById()', true),
  ('web-q11-b', 'web-q11', 2, 'document.getClassById()', false),
  ('web-q11-c', 'web-q11', 3, 'document.findId()', false),
  ('web-q11-d', 'web-q11', 4, 'document.querySelectorId()', false),

  ('web-q12-a', 'web-q12', 1, '<script>', true),
  ('web-q12-b', 'web-q12', 2, '<js>', false),
  ('web-q12-c', 'web-q12', 3, '<link>', false),
  ('web-q12-d', 'web-q12', 4, '<code>', false),

  ('web-q13-a', 'web-q13', 1, 'Criar layouts flexíveis com alinhamento e distribuição de espaço', true),
  ('web-q13-b', 'web-q13', 2, 'Animações automáticas entre páginas', false),
  ('web-q13-c', 'web-q13', 3, 'Aplicar sombras e bordas arredondadas', false),
  ('web-q13-d', 'web-q13', 4, 'Validar campos de formulário', false),

  ('web-q14-a', 'web-q14', 1, 'Usa criptografia (TLS/SSL) na comunicação', true),
  ('web-q14-b', 'web-q14', 2, 'Carrega as páginas mais rápido', false),
  ('web-q14-c', 'web-q14', 3, 'Funciona apenas em navegadores pagos', false),
  ('web-q14-d', 'web-q14', 4, 'É um protocolo exclusivo para download de arquivos', false),

  ('web-q15-a', 'web-q15', 1, 'var', false),
  ('web-q15-b', 'web-q15', 2, 'let', false),
  ('web-q15-c', 'web-q15', 3, 'const', true),
  ('web-q15-d', 'web-q15', 4, 'static', false),

  ('web-q16-a', 'web-q16', 1, 'Vermelho (red)', false),
  ('web-q16-b', 'web-q16', 2, 'Azul (blue)', true),
  ('web-q16-c', 'web-q16', 3, 'Verde (green)', false),
  ('web-q16-d', 'web-q16', 4, 'Laranja (orange)', false)
ON CONFLICT (id) DO UPDATE SET
  position = EXCLUDED.position,
  label = EXCLUDED.label,
  is_correct = EXCLUDED.is_correct;
