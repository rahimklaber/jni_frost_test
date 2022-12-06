use flapigen::{JavaConfig, LanguageConfig, JavaReachabilityFence};
use std::{path::Path};
use rifgen::{Generator, TypeCases, Language};

fn main() {
    // let source_folder = "src"; //use your projects folder
    // let out_file = "src/glue.in";
    // Generator::new(TypeCases::CamelCase,Language::Java,source_folder)
    // .generate_interface(out_file);

    let in_src = Path::new("src").join("glue.in");
    let out_src = Path::new("out").join("java_glue.rs");
    //ANCHOR: config
    let swig_gen = flapigen::Generator::new(LanguageConfig::JavaConfig(
        JavaConfig::new(
            Path::new("out")
            .to_path_buf()
                // .join("src")
                // .join("main")
                // .join("java")
                // .join("net")
                // .join("akaame")
                // .join("myapplication")
                ,
            "generated".into(),
        )
        .use_reachability_fence(JavaReachabilityFence::Std)
        // .use_null_annotation_from_package("android.support.annotation".into()),
    ))
    .rustfmt_bindings(true);
    //ANCHOR_END: config
    swig_gen.expand("android bindings", &in_src, &out_src);
    println!("cargo:rerun-if-changed={}", in_src.display());
}