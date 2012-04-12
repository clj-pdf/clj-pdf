# A Library for easily generating PDFs from Clojure 

## Usage

write-doc will produce a PDF given a vector which defines the document and write it to out which can be either a string, in which case it's treated as a file name, or an output stream. The document format is as follows:

### Metadata

All fields in the metadata section are optional:

    {:title  "Test doc"
     :left-margin   10
     :right-margin  10
     :top-margin    20
     :bottom-margin 25
     :subject "Some subject"
     :author "John Doe"
     :creator "Jane Doe"
     :header ["inspired by" "William Shakespeare"]}

### Document sections

Each document section is represented by a vector starting with a keyword identifying the section followed by the contents of the section.

#### Font

A font is defined by a map consisting of the following parameters, all parameters are optional

* :family has following options: :courier, :helvetica, :times-roman, :symbol, :zapfdingbats defaults to :helvetica
* :size is a number default is 11
* :style has following options: :bold, :italic, :bold-italic, :normal, :strikethru, :underline defaults to :normal
* :color is a vector of [r g b] defaults to black

example font:

    {:style :bold
     :size 18
     :family :helvetica
     :color [0 234 123]}


#### Anchor

An anchor can be a reference or a destination of a reference, it consists of the keyword :anchor followed by optional metadata and the content. The content consists of the following fields:

* :style font
* :leading number

    {:style {:size 15 :style :bold} :leading 20}
    
iText idiosynchorsies:

* when both font style and leading number are specified the content must be a string
* when leading number is specified content can be a chunk or a string 
* when only font style is specified content must be a string
* if no font style or leading is specified then content can be a chunk, a phrase, or a string

    [:anchor {:style {:size 15} :leading 20} "some anchor"]
    
    [:anchor {}  [:phrase {:style :bold} "some anchor phrase"]]
    
    [:anchor {} "plain anchor"]


#### Chunk 

Chunk is the smallest component, it consists of the keyword :chunk a font and the content string 

   [:chunk {:style :bold} "small chunk of text"]

#### Phrase

A phrase consists of the keyword :phrase followed by the contents, the first item of contents is the font metadata (can be empty), followed by the rest of the contents, which can conist of chunks or strings:

    [:phrase {} "some text here"]

    [:phrase {:style :bold :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]
  
    [:phrase [:chunk {:style :italic} "chunk one"] [:chunk {:size 20} "Big text"] "some other text"]

#### Paragraph

A paragraph consists of the keyword :paragraph followed by the metadata (can be empty) and the content which can consists of either a string or a phrase.

The metadata consists of the following fields

* :indent number
* :keep-together boolean

    [:paragraph {} "a fine paragraph"]
    
    [:paragraph {:keep-together true :indent 20} "a fine paragraph"]

    [:paragraph {:indent 50} [:phrase {:style :bold :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]]

#### Chapter

A chapter consists of the keyword :chapter and  title which can be either a string or a paragraph:


    [:chapter "First Chapter"]

    [:chapter [:paragraph {} "Second Chapter"]]

#### List

A list consists of the keyword :list followed by the metadata and the contents, the metadata can consist of the following, all omitable:

* :numbered boolean
* :lettered boolean
* :roman    boolean

the rest of the contents can be either strings, phrases, or chunks

   [:list {:roman true} [:chunk {:style :bold} "a bold item"] "another item" "yet another item"]


### A complete example

    (write-doc [{:title  "Test doc"
                 :left-margin   10
                 :right-margin  10
                 :top-margin    20
                 :bottom-margin 25
                 :subject "Some subject"
                 :author "John Doe"
                 :creator "Jane Doe"
                 :header ["inspired by" "William Shakespeare"]}
            
                [:anchor {:style {:size 15} :leading 20} "some anchor"]
            
                [:anchor {}  [:phrase {:style :bold} "some anchor phrase"]]
            
                [:anchor {} "plain anchor"]        
                
                [:chunk {:style :bold} "small chunk of text"]
            
                [:phrase {} "some text here"]
            
                [:phrase {:style :bold :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]
            
                [:phrase [:chunk {:style :italic} "chunk one"] [:chunk {:size 20} "Big text"] "some other text"]
            
                [:paragraph {} "is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum."]
            
                [:paragraph {:keep-together true :indent 20} "a fine paragraph"]
            
                [:paragraph {:indent 50} [:phrase {:style :bold :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]]
            
                [:chapter "First Chapter"]
            
                [:chapter [:paragraph {} "Second Chapter"]]
            
                [:list {:roman true} [:chunk {:style :bold} "a bold item"] "another item" "yet another item"]]
               "test.pdf")


# TODO:

* support for images
* page sizes
* suggestions welcome :)







